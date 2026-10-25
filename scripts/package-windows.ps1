param(
    [ValidateSet('exe','msi','portable','all')][string]$Type = 'all',
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 25 installation.' }
$javaBin = Join-Path $env:JAVA_HOME 'bin'
$javaVersion = & (Join-Path $javaBin 'java.exe') -version 2>&1 | Out-String
if ($javaVersion -notmatch 'version "25[.\"]') { throw 'JDK 25 is required.' }
if (-not $SkipBuild) {
    & .\mvnw.cmd -B -ntp verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed.' }
}
$releaseRoot = Join-Path $projectRoot 'release'
$stagingRoot = Join-Path $projectRoot 'target\package-input'
$moduleRoot = Join-Path $projectRoot 'target\fx-modules'
$runtimeRoot = Join-Path $projectRoot 'target\runtime'
function Reset-GeneratedDirectory([string]$candidate) {
    $resolved = [System.IO.Path]::GetFullPath($candidate)
    $targetRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'target')) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($targetRoot,[System.StringComparison]::OrdinalIgnoreCase)) { throw "Refusing to remove path outside target: $resolved" }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
    New-Item -ItemType Directory -Path $resolved | Out-Null
}
Reset-GeneratedDirectory $stagingRoot
Reset-GeneratedDirectory $moduleRoot
Copy-Item -LiteralPath 'target\enro.jar' -Destination $stagingRoot
New-Item -ItemType Directory -Path (Join-Path $stagingRoot 'lib') | Out-Null
Get-ChildItem -LiteralPath 'target\lib' -Filter '*.jar' | ForEach-Object {
    if ($_.Name -like 'javafx-*-win.jar') { Copy-Item -LiteralPath $_.FullName -Destination $moduleRoot }
    elseif ($_.Name -notlike 'javafx-*') { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $stagingRoot 'lib') }
}
Reset-GeneratedDirectory $runtimeRoot
# jlink requires a destination that does not exist. It has just been validated above.
Remove-Item -LiteralPath $runtimeRoot
& (Join-Path $javaBin 'jlink.exe') --module-path "$env:JAVA_HOME\jmods;$moduleRoot" --add-modules 'java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.zipfs,javafx.controls,javafx.fxml' --strip-debug --no-header-files --no-man-pages --output $runtimeRoot
if ($LASTEXITCODE -ne 0) { throw 'jlink failed.' }
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
$portableRoot = Join-Path $releaseRoot 'Enro'
if (Test-Path -LiteralPath $portableRoot) {
    # Only remove the exact generated app image, after checking its marker.
    $resolvedPortable = [System.IO.Path]::GetFullPath($portableRoot)
    if ($resolvedPortable -ne [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'release\Enro')) -or -not (Test-Path -LiteralPath (Join-Path $portableRoot 'app\Enro.cfg'))) { throw 'Existing release/Enro is not a recognized generated app image.' }
    Remove-Item -LiteralPath $resolvedPortable -Recurse -Force
}
$common = @('--name','Enro','--app-version','2.0.0','--vendor','Enro','--description','Intelligent Storage & Cleanup Manager','--copyright','Copyright 2026 Enro contributors','--dest',$releaseRoot,'--icon',(Join-Path $projectRoot 'src\main\resources\icons\enro.ico'))
$imageArguments = @('--type','app-image','--input',$stagingRoot,'--main-jar','enro.jar','--main-class','com.enro.app.Launcher','--runtime-image',$runtimeRoot,'--java-options','--add-modules=javafx.controls,javafx.fxml','--java-options','--enable-native-access=ALL-UNNAMED,javafx.graphics','--java-options','-Xmx768m')
& (Join-Path $javaBin 'jpackage.exe') @common @imageArguments
if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed.' }
Copy-Item -LiteralPath 'START_HERE.md','SAFETY_GUIDE.md','USER_GUIDE.md','THIRD_PARTY_NOTICES.md' -Destination $portableRoot
if ($Type -in @('all','portable')) {
    Compress-Archive -LiteralPath $portableRoot -DestinationPath (Join-Path $releaseRoot 'Enro-Portable-2.0.0-win-x64.zip') -Force
}
$installerTypes = if ($Type -eq 'all') { @('exe','msi') } elseif ($Type -in @('exe','msi')) { @($Type) } else { @() }
foreach ($installerType in $installerTypes) {
    $oldInstaller = Join-Path $releaseRoot "Enro-2.0.0.$installerType"
    if (Test-Path -LiteralPath $oldInstaller) { Remove-Item -LiteralPath $oldInstaller -Force }
    & (Join-Path $javaBin 'jpackage.exe') @common --type $installerType --app-image $portableRoot --win-per-user-install --win-dir-chooser --win-shortcut --win-menu --win-menu-group 'Enro' --win-upgrade-uuid 'a17627b9-d49c-4310-b9db-92b987871cc9'
    if ($LASTEXITCODE -ne 0) { throw "jpackage $installerType failed. Install the WiX version supported by your JDK's jpackage." }
}
Get-ChildItem -LiteralPath $releaseRoot -File | Where-Object { $_.Extension -in @('.zip','.exe','.msi') } | Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLowerInvariant())  $([System.IO.Path]::GetFileName($_.Path))" } | Set-Content -LiteralPath (Join-Path $releaseRoot 'SHA256SUMS.txt') -Encoding ascii
Write-Output "Release artifacts: $releaseRoot"
