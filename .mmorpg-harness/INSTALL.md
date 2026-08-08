# One-time Windows bootstrap

The bootstrap does not use or modify the developer working copy. It creates `%LOCALAPPDATA%\\BranzMMORPGHarness\\repo`, fetches the GitHub control branch, runs offline selftests, registers the `BranzMMORPGHarnessDaemon` Scheduled Task, and starts it.

Run once from PowerShell:

```powershell
$u = 'https://raw.githubusercontent.com/BranzX3/branz-mmorpg/HARNESS_MMORPG_CONTROL/.mmorpg-harness/bootstrap.ps1'
$p = Join-Path $env:TEMP 'branz-mmorpg-harness-bootstrap.ps1'
Invoke-WebRequest -UseBasicParsing $u -OutFile $p
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $p
```

After bootstrap, the daemon fetches and self-updates from GitHub. Do not copy harness files into another working copy.
