Add-Type -AssemblyName System.Drawing
$sourcePath = "C:\Users\Kenneth Lumod\.gemini\antigravity-ide\brain\df739006-5305-48b0-a161-aca3baa45028\.user_uploaded\media_1786980931787.jpg"
$img = [System.Drawing.Image]::FromFile($sourcePath)

$resolutions = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($folder in $resolutions.Keys) {
    $size = $resolutions[$folder]
    $destDir = Join-Path "app\src\main\res" $folder
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force }
    
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($img, 0, 0, $size, $size)
    $g.Dispose()
    
    $bmp.Save((Join-Path $destDir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save((Join-Path $destDir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$img.Dispose()
Write-Host "Mipmap icons updated successfully."
