param(
    [string]$WorkerRoot = "$env:LOCALAPPDATA\BranzMMORPGHarness",
    [string]$RepositoryUrl = 'https://github.com/BranzX3/branz-mmorpg.git',
    [string]$ControlBranch = 'HARNESS_MMORPG_CONTROL',
    [int]$PollSeconds = 30
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($ControlBranch -ne 'HARNESS_MMORPG_CONTROL') {
    throw "MMORPG Harness control branch is fixed to HARNESS_MMORPG_CONTROL; got $ControlBranch"
}
if ($RepositoryUrl -notmatch '(?i)^https://github\.com/BranzX3/branz-mmorpg(?:\.git)?$') {
    throw "MMORPG Harness repository is fixed to BranzX3/branz-mmorpg; got $RepositoryUrl"
}
if ($PollSeconds -lt 10 -or $PollSeconds -gt 3600) {
    throw "PollSeconds must be between 10 and 3600; got $PollSeconds"
}

function Get-FullPath([string]$Path) {
    return [IO.Path]::GetFullPath([Environment]::ExpandEnvironmentVariables($Path)).TrimEnd('\')
}

function Assert-IsolatedRoot([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        throw 'LOCALAPPDATA is required for the MMORPG Harness.'
    }
    $candidate = Get-FullPath $Path
    $tradebot = Get-FullPath (Join-Path $env:LOCALAPPDATA 'TradebotHarness')
    if ($candidate.Equals($tradebot, [StringComparison]::OrdinalIgnoreCase) -or
        $candidate.StartsWith($tradebot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "MMORPG Harness WorkerRoot must not be TradebotHarness or a descendant: $candidate"
    }
    return $candidate
}

function Normalize-Text([string]$Text) {
    return (($Text -replace "`r`n", "`n" -replace "`r", "`n").TrimEnd("`n") + "`n")
}

function Write-Utf8NoBomAtomic([string]$Path, [string]$Text) {
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $tmp = "$Path.tmp.$PID"
    [IO.File]::WriteAllText($tmp, (Normalize-Text $Text), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $tmp -Destination $Path -Force
}

$WorkerRoot = Assert-IsolatedRoot $WorkerRoot
$repo = Join-Path $WorkerRoot 'repo'
$runtime = Join-Path $WorkerRoot 'runtime'
$logs = Join-Path $WorkerRoot 'logs'
$gradleHome = Join-Path $WorkerRoot 'gradle-home'
$runner = Join-Path $runtime 'runner.py'
$selftest = Join-Path $runtime 'selftest.py'
$toolchainFile = Join-Path $runtime 'bootstrap-environment.json'
$restartScript = Join-Path $runtime 'restart-daemon.ps1'
$script:RestartRequested = $false
$script:GitExe = $null
$script:PythonExe = $null
New-Item -ItemType Directory -Force -Path $WorkerRoot,$runtime,$logs,$gradleHome | Out-Null

function Write-DaemonLog([string]$Message) {
    $stamp = (Get-Date).ToUniversalTime().ToString('o')
    Add-Content -Encoding UTF8 -Path (Join-Path $logs 'daemon.log') -Value "[$stamp] $Message"
}

function Initialize-Toolchain {
    if (-not (Test-Path -LiteralPath $toolchainFile -PathType Leaf)) {
        throw "Pinned bootstrap toolchain is missing: $toolchainFile"
    }
    try {
        $cfg = Get-Content -Raw -LiteralPath $toolchainFile -Encoding UTF8 | ConvertFrom-Json
    }
    catch {
        throw "Pinned bootstrap toolchain is invalid JSON: $($_.Exception.Message)"
    }
    if ([int]$cfg.schema_version -ne 1) { throw "Unsupported toolchain schema: $($cfg.schema_version)" }
    $gitExe = [string]$cfg.git_exe
    $pythonExe = [string]$cfg.python_exe
    $javaHome = [string]$cfg.java_home
    foreach ($entry in @($gitExe,$pythonExe,(Join-Path $javaHome 'bin\java.exe'))) {
        if ([string]::IsNullOrWhiteSpace($entry) -or -not (Test-Path -LiteralPath $entry -PathType Leaf)) {
            throw "Pinned toolchain executable is unavailable: $entry"
        }
    }
    $script:GitExe = (Resolve-Path -LiteralPath $gitExe).Path
    $script:PythonExe = (Resolve-Path -LiteralPath $pythonExe).Path
    $javaHome = (Resolve-Path -LiteralPath $javaHome).Path
    $env:JAVA_HOME = $javaHome
    $prefix = @(
        (Split-Path -Parent $script:GitExe),
        (Split-Path -Parent $script:PythonExe),
        (Join-Path $javaHome 'bin')
    ) -join ';'
    $env:PATH = "$prefix;$env:PATH"
}

function Invoke-NativeCaptured {
    param(
        [Parameter(Mandatory=$true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = ''
    )
    $oldPreference = $ErrorActionPreference
    $output = @()
    $code = 0
    try {
        $ErrorActionPreference = 'Continue'
        if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) { Push-Location $WorkingDirectory }
        try {
            $output = @(& $FilePath @Arguments 2>&1)
            $code = $LASTEXITCODE
        }
        finally {
            if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) { Pop-Location }
        }
    }
    finally { $ErrorActionPreference = $oldPreference }
    return [pscustomobject]@{
        ExitCode = [int]$code
        Lines = @($output | ForEach-Object { $_.ToString() })
    }
}

function Write-NativeLines($Result) {
    foreach ($line in $Result.Lines) {
        if (-not [string]::IsNullOrWhiteSpace($line)) { Write-DaemonLog $line }
    }
}

function Assert-WorkerOrigin {
    $origin = Invoke-NativeCaptured -FilePath $script:GitExe -Arguments @('-C', $repo, 'remote', 'get-url', 'origin')
    if ($origin.ExitCode -ne 0) { throw 'Worker clone has no readable origin.' }
    $url = ($origin.Lines -join '').Trim()
    if ($url -notmatch '(?i)(github\.com[/:])BranzX3/branz-mmorpg(?:\.git)?$') {
        throw "Worker clone origin is not BranzX3/branz-mmorpg: $url"
    }
}

function Ensure-WorkerClone {
    if (Test-Path (Join-Path $repo '.git')) {
        Assert-WorkerOrigin
        return
    }
    if (Test-Path $repo) {
        throw "Worker path exists but is not a Git repository; refusing destructive recovery: $repo"
    }
    Write-DaemonLog 'Creating dedicated MMORPG worker clone.'
    $clone = Invoke-NativeCaptured -FilePath $script:GitExe -Arguments @('clone', '--no-tags', $RepositoryUrl, $repo)
    Write-NativeLines $clone
    if ($clone.ExitCode -ne 0) { throw "git clone failed: $($clone.ExitCode)" }
    Assert-WorkerOrigin
    foreach ($pair in @(
        @('user.name','branz-mmorpg-harness'),
        @('user.email','branz-mmorpg-harness@users.noreply.github.com'),
        @('core.autocrlf','false'),
        @('fetch.prune','true')
    )) {
        $cfg = Invoke-NativeCaptured -FilePath $script:GitExe -Arguments @('-C', $repo, 'config', $pair[0], $pair[1])
        if ($cfg.ExitCode -ne 0) { throw "git config failed for $($pair[0])" }
    }
}

function Fetch-Origin {
    $fetch = Invoke-NativeCaptured -FilePath $script:GitExe -Arguments @('-C', $repo, 'fetch', 'origin', '--prune', '--no-tags')
    Write-NativeLines $fetch
    if ($fetch.ExitCode -ne 0) { throw "git fetch failed: $($fetch.ExitCode)" }
}

function Read-RemoteFile([string]$Path) {
    $show = Invoke-NativeCaptured -FilePath $script:GitExe -Arguments @('-C', $repo, 'show', "origin/$ControlBranch`:$Path")
    if ($show.ExitCode -ne 0) {
        Write-NativeLines $show
        throw "Could not read remote $Path from origin/$ControlBranch."
    }
    return (Normalize-Text ($show.Lines -join "`n"))
}

function Get-ControlCommit {
    $rev = Invoke-NativeCaptured -FilePath $script:GitExe -Arguments @('-C', $repo, 'rev-parse', "origin/$ControlBranch")
    if ($rev.ExitCode -ne 0) { throw "Control branch is unavailable: $ControlBranch" }
    $sha = ($rev.Lines -join '').Trim()
    if ($sha -notmatch '^[0-9a-f]{40}$') { throw "Invalid control commit: $sha" }
    return $sha
}

function Refresh-ControlRuntime {
    Fetch-Origin
    $controlCommit = Get-ControlCommit
    $remoteDaemon = Read-RemoteFile '.mmorpg-harness/daemon.ps1'
    $currentDaemon = Normalize-Text ([IO.File]::ReadAllText($PSCommandPath))
    if ($remoteDaemon -ne $currentDaemon) {
        Write-Utf8NoBomAtomic -Path $PSCommandPath -Text $remoteDaemon
        Write-DaemonLog "DAEMON_SELF_UPDATE_APPLIED control=$controlCommit"
        $script:RestartRequested = $true
        return $null
    }
    $remoteRunner = Read-RemoteFile '.mmorpg-harness/runner.py'
    $remoteSelftest = Read-RemoteFile '.mmorpg-harness/selftest.py'
    $runtimeChanged = $false
    if (-not (Test-Path $runner) -or (Normalize-Text ([IO.File]::ReadAllText($runner))) -ne $remoteRunner) {
        Write-Utf8NoBomAtomic -Path $runner -Text $remoteRunner
        $runtimeChanged = $true
    }
    if (-not (Test-Path $selftest) -or (Normalize-Text ([IO.File]::ReadAllText($selftest))) -ne $remoteSelftest) {
        Write-Utf8NoBomAtomic -Path $selftest -Text $remoteSelftest
        $runtimeChanged = $true
    }
    if ($runtimeChanged) {
        $test = Invoke-NativeCaptured -FilePath $script:PythonExe -Arguments @($selftest) -WorkingDirectory $runtime
        Write-NativeLines $test
        if ($test.ExitCode -ne 0 -or ($test.Lines -join "`n") -notmatch 'MMORPG_HARNESS_SELFTEST_PASS') {
            throw "Updated runtime failed selftest; task execution is blocked. Exit=$($test.ExitCode)"
        }
        Write-DaemonLog "RUNTIME_SELF_UPDATE_VERIFIED control=$controlCommit"
    }
    return $controlCommit
}

function Prepare-RestartScript {
    $daemonEsc = $PSCommandPath.Replace("'", "''")
    $rootEsc = $WorkerRoot.Replace("'", "''")
    $repoUrlEsc = $RepositoryUrl.Replace("'", "''")
    $branchEsc = $ControlBranch.Replace("'", "''")
    $body = @"
Start-Sleep -Seconds 2
& '$daemonEsc' -WorkerRoot '$rootEsc' -RepositoryUrl '$repoUrlEsc' -ControlBranch '$branchEsc' -PollSeconds $PollSeconds
"@
    Write-Utf8NoBomAtomic -Path $restartScript -Text $body
}

$mutex = $null
try {
    Initialize-Toolchain
    $mutex = New-Object System.Threading.Mutex($false, 'Local\BranzMMORPGHarnessDaemon')
    if (-not $mutex.WaitOne(0, $false)) { exit 0 }
    Write-DaemonLog "Daemon starting. root=$WorkerRoot poll=$PollSeconds control=$ControlBranch"
    while ($true) {
        try {
            Ensure-WorkerClone
            $controlCommit = Refresh-ControlRuntime
            if ($script:RestartRequested) {
                Prepare-RestartScript
                break
            }
            if ([string]::IsNullOrWhiteSpace($controlCommit)) {
                throw 'Control commit unavailable after runtime refresh.'
            }
            $oldControl = $env:BRANZ_MMO_CONTROL_COMMIT
            $oldGradle = $env:GRADLE_USER_HOME
            try {
                $env:BRANZ_MMO_CONTROL_COMMIT = $controlCommit
                $env:GRADLE_USER_HOME = $gradleHome
                $run = Invoke-NativeCaptured -FilePath $script:PythonExe -Arguments @($runner, 'run') -WorkingDirectory $repo
                Write-NativeLines $run
                if ($run.ExitCode -notin @(0,10,20,22)) {
                    Write-DaemonLog "Unexpected runner exit code: $($run.ExitCode)"
                }
            }
            finally {
                $env:BRANZ_MMO_CONTROL_COMMIT = $oldControl
                $env:GRADLE_USER_HOME = $oldGradle
            }
        }
        catch {
            Write-DaemonLog ("LOOP_BLOCKED " + $_.Exception.Message)
        }
        Start-Sleep -Seconds $PollSeconds
    }
}
catch {
    Write-DaemonLog ("DAEMON_FATAL " + $_.Exception.Message)
    exit 1
}
finally {
    if ($null -ne $mutex) {
        Write-DaemonLog 'Daemon stopping.'
        try { $mutex.ReleaseMutex() | Out-Null } catch {}
        $mutex.Dispose()
    }
}

if ($script:RestartRequested) {
    Start-Process -FilePath 'powershell.exe' -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$restartScript`"" -WindowStyle Hidden | Out-Null
}
