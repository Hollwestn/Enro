$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')
& .\mvnw.cmd -B -ntp javafx:run
exit $LASTEXITCODE
