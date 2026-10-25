param([switch]$NativeTests)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')
if ($NativeTests) { & .\mvnw.cmd -B -ntp '-Denro.nativeTests=true' verify }
else { & .\mvnw.cmd -B -ntp verify }
exit $LASTEXITCODE
