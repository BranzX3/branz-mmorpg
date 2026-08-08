param(
    [string]$WorkerRoot = "$env:LOCALAPPDATA\BranzMMORPGHarness",
    [string]$TaskName = 'BranzMMORPGHarnessDaemon',
    [string]$RepositoryUrl = 'https://github.com/BranzX3/branz-mmorpg.git',
    [string]$ControlBranch = 'HARNESS_MMORPG_CONTROL',
    [switch]$RegisterOnly,
    [string]$RunAsUser = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($TaskName -ne 'BranzMMORPGHarnessDaemon') {
    throw "MMORPG Harness Scheduled Task name is fixed to BranzMMORPGHarnessDaemon; got $TaskName"
}
if ($ControlBranch -ne 'HARNESS_MMORPG_CONTROL') {
    throw "MMORPG Harness control branch is fixed to HARNESS_MMORPG_CONTROL; got $ControlBranch"
}
if ($RepositoryUrl -notmatch '(?i)^https://github\.com/BranzX3/branz-mmorpg(?:\.git)?$') {
    throw "MMORPG Harness repository is fixed to BranzX3/branz-mmorpg; got $RepositoryUrl"
}

function Get-FullPath([string]$Path) {
    return [IO.Path]::GetFullPath([Environment]::ExpandEnvironmentVariables($Path)).TrimEnd('\')
}

function Assert-IsolatedRoot([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) { throw 'LOCALAPPDATA is required.' }
    $candidate = Get-FullPath $Path
    $tradebot = Get-FullPath (Join-Path $env:LOCALAPPDATA 'TradebotHarness')
    if ($candidate.Equals($tradebot, [StringComparison]::OrdinalIgnoreCase) -or
        $candidate.StartsWith($tradebot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing WorkerRoot inside TradebotHarness: $candidate"
    }
    return $candidate
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

function Normalize-Text([string]$Text) {
    return (($Text -replace "`r`n", "`n" -replace "`r", "`n").TrimEnd("`n") + "`n")
}

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    [IO.File]::WriteAllText($Path, (Normalize-Text $Text), [Text.UTF8Encoding]::new($false))
}

function Resolve-Application([string]$Name) {
    $cmd = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $cmd -or [string]::IsNullOrWhiteSpace($cmd.Source)) {
        throw "$Name executable is required."
    }
    return (Resolve-Path -LiteralPath $cmd.Source).Path
}

$WorkerRoot = Assert-IsolatedRoot $WorkerRoot
$logs = Join-Path $WorkerRoot 'logs'
$runtime = Join-Path $WorkerRoot 'runtime'
$repo = Join-Path $WorkerRoot 'repo'
$gradleHome = Join-Path $WorkerRoot 'gradle-home'
$bootstrapLog = Join-Path $logs 'bootstrap.log'
$registerLog = Join-Path $logs 'register-task.log'
$daemonInstalled = Join-Path $WorkerRoot 'daemon.ps1'
$toolchainFile = Join-Path $runtime 'bootstrap-environment.json'
New-Item -ItemType Directory -Force -Path $WorkerRoot,$logs,$runtime,$gradleHome | Out-Null

function Register-DaemonTask([string]$Account) {
    if ([string]::IsNullOrWhiteSpace($Account)) { throw 'RunAsUser is required for task registration.' }
    if (-not (Test-Path -LiteralPath $daemonInstalled -PathType Leaf)) {
        throw "Installed daemon is missing: $daemonInstalled"
    }
    $psExe = Join-Path $PSHOME 'powershell.exe'
    if (-not (Test-Path -LiteralPath $psExe -PathType Leaf)) {
        $psExe = (Get-Process -Id $PID).Path
    }
    $actionArgs = "-NoProfile -ExecutionPolicy Bypass -File `"$daemonInstalled`" -WorkerRoot `"$WorkerRoot`" -RepositoryUrl `"$RepositoryUrl`" -ControlBranch `"$ControlBranch`""
    $action = New-ScheduledTaskAction -Execute $psExe -Argument $actionArgs -WorkingDirectory $WorkerRoot
    $logonTrigger = New-ScheduledTaskTrigger -AtLogOn -User $Account
    $watchdogTrigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 5) -RepetitionDuration (New-TimeSpan -Days 3650)
    $principal = New-ScheduledTaskPrincipal -UserId $Account -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1)
    $existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($existing) { Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue }
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger @($logonTrigger,$watchdogTrigger) -Principal $principal -Settings $settings -Force | Out-Null
    Start-ScheduledTask -TaskName $TaskName
}

if ($RegisterOnly) {
    try {
        Register-DaemonTask -Account $RunAsUser
        Add-Content -Encoding UTF8 -Path $registerLog -Value "[$((Get-Date).ToUniversalTime().ToString('o'))] REGISTER_ONLY_PASS user=$RunAsUser"
        exit 0
    }
    catch {
        $message = $_ | Out-String
        Add-Content -Encoding UTF8 -Path $registerLog -Value $message
        Write-Error $message
        exit 1
    }
}

Start-Transcript -Path $bootstrapLog -Append | Out-Null
try {
    # Resolve tools before any elevation. Git credentials and the developer-selected
    # JDK/Python context must remain the normal interactive user's context.
    $gitExe = Resolve-Application 'git'
    $pythonExe = Resolve-Application 'python'
    $javaExe = Resolve-Application 'java'

    $javaVersion = Invoke-NativeCaptured -FilePath $javaExe -Arguments @('-version')
    $javaText = ($javaVersion.Lines -join "`n")
    if ($javaVersion.ExitCode -ne 0 -or $javaText -notmatch '(?m)(?:java|openjdk) version "25(?:\.|\")') {
        throw "Harness requires JDK 25 in this PowerShell user context. Detected:`n$javaText"
    }
    $javaProps = Invoke-NativeCaptured -FilePath $javaExe -Arguments @('-XshowSettings:properties','-version')
    $propsText = ($javaProps.Lines -join "`n")
    $homeMatch = [regex]::Match($propsText, '(?m)^\s*java\.home\s*=\s*(.+?)\s*$')
    if (-not $homeMatch.Success) { throw "Could not resolve java.home from JDK 25.`n$propsText" }
    $javaHome = (Resolve-Path -LiteralPath $homeMatch.Groups[1].Value.Trim()).Path
    $pinnedJava = Join-Path $javaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $pinnedJava -PathType Leaf)) { throw "Resolved java.home has no java.exe: $javaHome" }

    $pythonProbe = Invoke-NativeCaptured -FilePath $pythonExe -Arguments @('--version')
    if ($pythonProbe.ExitCode -ne 0) { throw 'Pinned Python executable failed --version.' }
    $gitProbe = Invoke-NativeCaptured -FilePath $gitExe -Arguments @('--version')
    if ($gitProbe.ExitCode -ne 0) { throw 'Pinned Git executable failed --version.' }

    $toolchain = [ordered]@{
        schema_version = 1
        captured_at_utc = (Get-Date).ToUniversalTime().ToString('o')
        git_exe = $gitExe
        python_exe = $pythonExe
        java_home = $javaHome
    } | ConvertTo-Json -Depth 3
    Write-Utf8NoBom -Path $toolchainFile -Text $toolchain

    # Make the runner resolve the exact same toolchain in this process too.
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$(Split-Path -Parent $gitExe);$(Split-Path -Parent $pythonExe);$(Join-Path $javaHome 'bin');$env:PATH"

    if (Test-Path (Join-Path $repo '.git')) {
        $origin = Invoke-NativeCaptured -FilePath $gitExe -Arguments @('-C', $repo, 'remote', 'get-url', 'origin')
        if ($origin.ExitCode -ne 0) { throw 'Existing worker clone has no readable origin.' }
        $originUrl = ($origin.Lines -join '').Trim()
        if ($originUrl -notmatch '(?i)(github\.com[/:])BranzX3/branz-mmorpg(?:\.git)?$') {
            throw "Existing WorkerRoot belongs to another repository: $originUrl"
        }
        $dirty = Invoke-NativeCaptured -FilePath $gitExe -Arguments @('-C', $repo, 'status', '--porcelain=v1', '--untracked-files=all')
        if ($dirty.ExitCode -ne 0) { throw 'Could not inspect existing worker clone.' }
        if (-not [string]::IsNullOrWhiteSpace(($dirty.Lines -join "`n"))) {
            throw 'Existing worker clone is dirty. Refusing destructive bootstrap recovery.'
        }
    }
    elseif (Test-Path $repo) {
        throw "Worker path exists but is not a Git repository: $repo"
    }
    else {
        Write-Host 'Creating dedicated Branz MMORPG worker clone...'
        $clone = Invoke-NativeCaptured -FilePath $gitExe -Arguments @('clone', '--no-tags', $RepositoryUrl, $repo)
        if ($clone.ExitCode -ne 0) { throw "git clone failed:`n$($clone.Lines -join "`n")" }
    }

    foreach ($pair in @(
        @('user.name','branz-mmorpg-harness'),
        @('user.email','branz-mmorpg-harness@users.noreply.github.com'),
        @('core.autocrlf','false'),
        @('fetch.prune','true')
    )) {
        $cfg = Invoke-NativeCaptured -FilePath $gitExe -Arguments @('-C', $repo, 'config', $pair[0], $pair[1])
        if ($cfg.ExitCode -ne 0) { throw "git config failed for $($pair[0])" }
    }

    $fetch = Invoke-NativeCaptured -FilePath $gitExe -Arguments @('-C', $repo, 'fetch', 'origin', '--prune', '--no-tags')
    if ($fetch.ExitCode -ne 0) { throw "Worker clone cannot fetch origin:`n$($fetch.Lines -join "`n")" }

    function Read-RemoteFile([string]$Path) {
        $show = Invoke-NativeCaptured -FilePath $gitExe -Arguments @('-C', $repo, 'show', "origin/$ControlBranch`:$Path")
        if ($show.ExitCode -ne 0) { throw "Cannot read $Path from origin/$ControlBranch." }
        return (Normalize-Text ($show.Lines -join "`n"))
    }

    $control = Invoke-NativeCaptured -FilePath $gitExe -Arguments @('-C', $repo, 'rev-parse', "origin/$ControlBranch")
    if ($control.ExitCode -ne 0) { throw "Control branch does not exist: $ControlBranch" }
    $controlCommit = ($control.Lines -join '').Trim()
    if ($controlCommit -notmatch '^[0-9a-f]{40}$') { throw "Invalid control commit: $controlCommit" }

    $runnerInstalled = Join-Path $runtime 'runner.py'
    $selftestInstalled = Join-Path $runtime 'selftest.py'
    Write-Utf8NoBom $daemonInstalled (Read-RemoteFile '.mmorpg-harness/daemon.ps1')
    Write-Utf8NoBom $runnerInstalled (Read-RemoteFile '.mmorpg-harness/runner.py')
    Write-Utf8NoBom $selftestInstalled (Read-RemoteFile '.mmorpg-harness/selftest.py')

    Write-Host 'Running offline protocol selftest...'
    $self = Invoke-NativeCaptured -FilePath $pythonExe -Arguments @($selftestInstalled) -WorkingDirectory $runtime
    foreach ($line in $self.Lines) { Write-Host $line }
    if ($self.ExitCode -ne 0 -or ($self.Lines -join "`n") -notmatch 'MMORPG_HARNESS_SELFTEST_PASS') {
        throw 'Harness offline selftest failed.'
    }

    $oldControl = [Environment]::GetEnvironmentVariable('BRANZ_MMO_CONTROL_COMMIT','Process')
    $oldGradle = [Environment]::GetEnvironmentVariable('GRADLE_USER_HOME','Process')
    try {
        $env:BRANZ_MMO_CONTROL_COMMIT = $controlCommit
        $env:GRADLE_USER_HOME = $gradleHome
        Write-Host 'Running GitHub-controlled bootstrap canary in normal user context...'
        $canary = Invoke-NativeCaptured -FilePath $pythonExe -Arguments @($runnerInstalled, 'run') -WorkingDirectory $repo
        foreach ($line in $canary.Lines) { Write-Host $line }
        if ($canary.ExitCode -ne 0) {
            throw "Bootstrap canary did not complete successfully. Exit=$($canary.ExitCode)"
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable('BRANZ_MMO_CONTROL_COMMIT',$oldControl,'Process')
        [Environment]::SetEnvironmentVariable('GRADLE_USER_HOME',$oldGradle,'Process')
    }

    $account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    try {
        Register-DaemonTask -Account $account
    }
    catch {
        # The daemon itself must stay non-elevated. If this Windows installation
        # requires elevation to register a task, elevate registration only.
        Write-Host 'Scheduled Task registration requires elevation; requesting UAC for registration only.'
        $argLine = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $PSCommandPath + '"'),
            '-WorkerRoot', ('"' + $WorkerRoot + '"'),
            '-TaskName', ('"' + $TaskName + '"'),
            '-RepositoryUrl', ('"' + $RepositoryUrl + '"'),
            '-ControlBranch', ('"' + $ControlBranch + '"'),
            '-RegisterOnly', '-RunAsUser', ('"' + $account + '"')
        ) -join ' '
        $reg = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $argLine -Wait -PassThru
        if ($reg.ExitCode -ne 0) {
            throw "Elevated Scheduled Task registration failed. Exit=$($reg.ExitCode); log=$registerLog"
        }
    }

    Start-Sleep -Seconds 3
    $task = Get-ScheduledTask -TaskName $TaskName
    $info = Get-ScheduledTaskInfo -TaskName $TaskName
    Write-Host ''
    Write-Host 'BRANZ_MMORPG_HARNESS_INSTALLED'
    Write-Host "Control commit: $controlCommit"
    Write-Host "Worker clone: $repo"
    Write-Host "Runtime root: $WorkerRoot"
    Write-Host "Pinned Java home: $javaHome"
    Write-Host "Scheduled task: $($task.TaskName) / $($task.State)"
    Write-Host "LastTaskResult: $($info.LastTaskResult)"
    Write-Host "Daemon log: $(Join-Path $logs 'daemon.log')"
    Write-Host 'GitHub task branches now carry deterministic result evidence; the development working copy is not used.'
    Stop-Transcript | Out-Null
    exit 0
}
catch {
    $message = $_ | Out-String
    Write-Error $message
    Add-Content -Encoding UTF8 -Path $bootstrapLog -Value $message
    try { Stop-Transcript | Out-Null } catch {}
    exit 1
}
