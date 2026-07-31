# Local Development

Requirements:

- JDK 25
- PowerShell on Windows, or a POSIX shell

Set `JAVA_HOME` to JDK 25 when an older Java runtime appears earlier on `PATH`.

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
.\gradlew.bat build
```

Useful commands:

```powershell
# Apply Java formatting
.\gradlew.bat spotlessApply

# Validate a compiled content manifest
.\gradlew.bat :mmo-content:run --args="validate C:\absolute\path\to\content-manifest.json"

# Boot Paper locally and shut it down after plugin enable
.\gradlew.bat :mmo-bootstrap:runServer -PsmokeTest=true
```

The Paper runner writes only to the ignored `mmo-bootstrap/run` directory. No task in this
repository deploys to a remote environment.
