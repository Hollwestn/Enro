$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$assetRoot=Join-Path $PSScriptRoot '..\src\main\resources\icons'
New-Item -ItemType Directory -Path $assetRoot -Force | Out-Null
$frames=[System.Collections.Generic.List[byte[]]]::new()
foreach($size in @(256,64,32,16)) {
    $bitmap=[System.Drawing.Bitmap]::new($size,$size)
    $graphics=[System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $accent=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(99,91,219))
    $outline=[System.Drawing.Drawing2D.GraphicsPath]::new()
    $radius=[single]($size*.30)
    $outline.AddArc(0,0,$radius,$radius,180,90)
    $outline.AddArc(($size-$radius),0,$radius,$radius,270,90)
    $outline.AddArc(($size-$radius),($size-$radius),$radius,$radius,0,90)
    $outline.AddArc(0,($size-$radius),$radius,$radius,90,90)
    $outline.CloseFigure()
    $graphics.FillPath($accent,$outline)
    $small=[System.Drawing.Pen]::new([System.Drawing.Color]::White,[single]($size*.075))
    $small.StartCap=[System.Drawing.Drawing2D.LineCap]::Round
    $small.EndCap=[System.Drawing.Drawing2D.LineCap]::Round
    $graphics.DrawLine($small,[single]($size*.36),[single]($size*.29),[single]($size*.36),[single]($size*.71))
    foreach($height in @(.29,.5,.71)){$graphics.DrawLine($small,[single]($size*.36),[single]($size*$height),[single]($size*.65),[single]($size*$height))}
    $stream=[System.IO.MemoryStream]::new()
    $bitmap.Save($stream,[System.Drawing.Imaging.ImageFormat]::Png)
    $frames.Add($stream.ToArray())
    if($size -eq 256){$bitmap.Save((Join-Path $assetRoot 'enro.png'),[System.Drawing.Imaging.ImageFormat]::Png)}
    $stream.Dispose();$small.Dispose();$accent.Dispose();$outline.Dispose();$graphics.Dispose();$bitmap.Dispose()
}
$iconStream=[System.IO.File]::Create((Join-Path $assetRoot 'enro.ico'))
$writer=[System.IO.BinaryWriter]::new($iconStream)
try {
    $writer.Write([uint16]0);$writer.Write([uint16]1);$writer.Write([uint16]4)
    $offset=6+16*4
    $sizes=@(0,64,32,16)
    for($i=0;$i -lt 4;$i++){$writer.Write([byte]$sizes[$i]);$writer.Write([byte]$sizes[$i]);$writer.Write([byte]0);$writer.Write([byte]0);$writer.Write([uint16]1);$writer.Write([uint16]32);$writer.Write([uint32]$frames[$i].Length);$writer.Write([uint32]$offset);$offset+=$frames[$i].Length}
    foreach($frame in $frames){$writer.Write($frame)}
}finally{$writer.Dispose();$iconStream.Dispose()}
