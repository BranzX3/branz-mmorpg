param(
    [string]$WorkerRoot = "$env:LOCALAPPDATA\BranzMMORPGHarness",
    [string]$TaskName = 'BranzMMORPGHarnessDaemon',
    [string]$RepositoryUrl = 'https://github.com/BranzX3/branz-mmorpg.git',
    [string]$ControlBranch = 'HARNESS_MMORPG_CONTROL',
    [switch]$Elevated
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

function Is-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object System.Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
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

$WorkerRoot = Assert-IsolatedRoot $WorkerRoot
$logs = Join-Path $WorkerRoot 'logs'
$bootstrapLog = Join-Path $logs 'bootstrap.log'

if (-not (Is-Administrator)) {
    New-Item -ItemType Directory -Force -Path $logs | Out-Null
    $elevationArgs = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $PSCommandPath + '"'),
        '-WorkerRoot', ('"' + $WorkerRoot + '"'),
        '-TaskName', ('"' + $TaskName + '"'),
        '-RepositoryUrl', ('"' + $RepositoryUrl + '"'),
        '-ControlBranch', ('"' + $ControlBranch + '"'),
        '-Elevated'
    ) -join ' '
    Write-Host 'Approve the one-time Windows UAC prompt for the Branz MMORPG Harness Scheduled Task.'
    $proc = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $elevationArgs -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        Write-Host "Bootstrap failed with exit code $($proc.ExitCode)."
        Write-Host "Bootstrap log: $bootstrapLog"
        exit $proc.ExitCode
    }
    Write-Host 'BRANZ_MMORPG_HARNESS_BOOTSTRAP_COMPLETE'
    Write-Host "WorkerRoot: $WorkerRoot"
    Write-Host "Bootstrap log: $bootstrapLog"
    exit 0
}

New-Item -ItemType Directory -Force -Path $WorkerRoot,$logs | Out-Null
Start-Transcript -Path $bootstrapLog -Append | Out-Null
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'git is required.' }
    if (-not (Get-Command python -ErrorAction SilentlyContinue)) { throw 'python is required.' }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'JDK 25 is required.' }

    $java = Invoke-NativeCaptured -FilePath 'java' -Arguments @('-version')
    $javaText = ($java.Lines -join "`n")
    if ($java.ExitCode -ne 0 -or $javaText -notmatch '(?m)(?:java|openjdk) version "25(?:\.|\")') {
        throw "Harness requires JDK 25 on PATH/JAVA_HOME. Detected:`n$javaText"
    }

    $repo = Join-Path $WorkerRoot 'repo'
    $runtime = Join-Path $WorkerRoot 'runtime'
    $gradleHome = Join-Path $WorkerRoot 'gradle-home'
    New-Item -ItemType Directory -Force -Path $runtime,$gradleHome | Out-Null

    if (Test-Path (Join-Path $repo '.git')) {
        $origin = Invoke-NativeCaptured -FilePath 'git' -Arguments @('-C', $repo, 'remote', 'get-url', 'origin')
        if ($origin.ExitCode -ne 0) { throw 'Existing worker clone has no readable origin.' }
        $originUrl = ($origin.Lines -join '').Trim()
        if ($originUrl -notmatch '(?i)(github\.com[/:])BranzX3/branz-mmorpg(?:\.git)?$') {
            throw "Existing WorkerRoot belongs to another repository: $originUrl"
        }
        $dirty = Invoke-NativeCaptured -FilePath 'git' -Arguments @('-C', $repo, 'status', '--porcelain=v1', '--untracked-files=all')
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
        $clone = Invoke-NativeCaptured -FilePath 'git' -Arguments @('clone', '--no-tags', $RepositoryUrl, $repo)
        if ($clone.ExitCode -ne 0) { throw "git clone failed:`n$($clone.Lines -join "`n")" }
    }

    foreach ($pair in @(
        @('user.name','branz-mmorpg-harness'),
        @('user.email','branz-mmorpg-harness@users.noreply.github.com'),
        @('core.autocrlf','false'),
        @('fetch.prune','true')
    )) {
        $cfg = Invoke-NativeCaptured -FilePath 'git' -Arguments @('-C', $repo, 'config', $pair[0], $pair[1])
        if ($cfg.ExitCode -ne 0) { throw "git config failed for $($pair[0])" }
    }

    $fetch = Invoke-NativeCaptured -FilePath 'git' -Arguments @('-C', $repo, 'fetch', 'origin', '--prune', '--no-tags')
    if ($fetch.ExitCode -ne 0) { throw "Worker clone cannot fetch origin:`n$($fetch.Lines -join "`n")" }

    function Read-RemoteFile([string]$Path) {
        $show = Invoke-NativeCaptured -FilePath 'git' -Arguments @('-C', $repo, 'show', "origin/$ControlBranch`:$Path")
        if ($show.ExitCode -ne 0) { throw "Cannot read $Path from origin/$ControlBranch." }
        return (Normalize-Text ($show.Lines -join "`n"))
    }

    $control = Invoke-NativeCaptured -FilePath 'git' -Arguments @('-C', $repo, 'rev-parse', "origin/$ControlBranch")
    if ($control.ExitCode -ne 0) { throw "Control branch does not exist: $ControlBranch" }
    $controlCommit = ($control.Lines -join '').Trim()
    if ($controlCommit -notmatch '^[0-9a-f]{40}$') { throw "Invalid control commit: $controlCommit" }

    $daemonInstalled = Join-Path $WorkerRoot 'daemon.ps1'
    $runnerInstalled = Join-Path $runtime 'runner.py'
    $selftestInstalled = Join-Path $runtime 'selftest.py'
    Write-Utf8NoBom $daemonInstalled (Read-RemoteFile '.mmorpg-harness/daemon.ps1')
    Write-Utf8NoBom $runnerInstalled (Read-RemoteFile '.mmorpg-harness/runner.py')
    Write-Utf8NoBom $selftestInstalled (Read-RemoteFile '.mmorpg-harness/selftest.py')

    Write-Host 'Running offline protocol selftest...'
    $self = Invoke-NativeCaptured -FilePath 'python' -Arguments @($selftestInstalled) -WorkingDirectory $runtime
    foreach ($line in $self.Lines) { Write-Host $line }
    if ($self.ExitCode -ne 0 -or ($self.Lines -join "`n") -notmatch 'MMORPG_HARNESS_SELFTEST_PASS') {
        throw 'Harness offline selftest failed.'
    }

    $oldControl = $env:BRANZ_MMO_CONTROL_COMMIT
    $oldGradle = $env:GRADLE_USER_HOME
    try {
        $env:BRANZ_MMO_CONTROL_COMMIT = $controlCommit
        $env:GRADLE_USER_HOME = $gradleHome
        Write-Host 'Running GitHub-controlled bootstrap canary...'
        $canary = Invoke-NativeCaptured -FilePath 'python' -Arguments @($runnerInstalled, 'run') -WorkingDirectory $repo
        foreach ($line in $canary.Lines) { Write-Host $line }
        if ($canary.ExitCode -ne 0) {
            throw "Bootstrap canary did not complete successfully. Exit=$($canary.ExitCode)"
        }
    }
    finally {
        $env:BRANZ_MMO_CONTROL_COMMIT = $oldControl
        $env:GRADLE_USER_HOME = $oldGradle
    }

    $account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $psExe = (Get-Process -Id $PID).Path
    $actionArgs = "-NoProfile -ExecutionPolicy Bypass -File `"$daemonInstalled`" -WorkerRoot `"$WorkerRoot`" -RepositoryUrl `"$RepositoryUrl`" -ControlBranch `"$ControlBranch`""
    $action = New-ScheduledTaskAction -Execute $psExe -Argument $actionArgs -WorkingDirectory $WorkerRoot
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $account
    $principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Highest
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew

    $existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($existing) { Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue }
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
    Start-ScheduledTask -TaskName $TaskName
    Start-Sleep -Seconds 3

    $task = Get-ScheduledTask -TaskName $TaskName
    $info = Get-ScheduledTaskInfo -TaskName $TaskName
    Write-Host ''
    Write-Host 'BRANZ_MMORPG_HARNESS_INSTALLED'
    Write-Host "Control commit: $controlCommit"
    Write-Host "Worker clone: $repo"
    Write-Host "Runtime root: $WorkerRoot"
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
