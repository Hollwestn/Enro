$ErrorActionPreference='Stop'
$projectRoot=[System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$releaseRoot=Join-Path $projectRoot 'release'
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
$archivePath=Join-Path $releaseRoot 'Enro-Source-2.0.0.zip'
if(Test-Path -LiteralPath $archivePath){Remove-Item -LiteralPath $archivePath -Force}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[System.IO.Compression.ZipFile]::Open($archivePath,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files=@()
    foreach($folder in @('src','docs','scripts','.mvn\wrapper','.github')){if(Test-Path -LiteralPath (Join-Path $projectRoot $folder)){$files+=Get-ChildItem -LiteralPath (Join-Path $projectRoot $folder) -File -Recurse -Force}}
    $files+=Get-ChildItem -LiteralPath $projectRoot -Filter '*.md' -File
    foreach($name in @('pom.xml','mvnw','mvnw.cmd','.gitignore','.gitattributes')){$files+=Get-Item -LiteralPath (Join-Path $projectRoot $name) -Force}
    foreach($file in $files){$relative=$file.FullName.Substring($projectRoot.Length+1).Replace('\','/');[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,('Enro/'+$relative),[System.IO.Compression.CompressionLevel]::Optimal)|Out-Null}
}finally{$archive.Dispose()}
Get-ChildItem -LiteralPath $releaseRoot -File | Where-Object { $_.Extension -in @('.zip','.exe','.msi') } | Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLowerInvariant())  $([System.IO.Path]::GetFileName($_.Path))" } | Set-Content -LiteralPath (Join-Path $releaseRoot 'SHA256SUMS.txt') -Encoding ascii
Write-Output $archivePath
