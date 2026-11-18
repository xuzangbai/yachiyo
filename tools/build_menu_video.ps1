param(
    [string]$Jdk = 'C:\Program Files\Java\jdk-21',
    [string]$Zig = 'zig'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$source = Join-Path $root 'src/main/native/menu_video.c'
$output = Join-Path $root 'src/main/resources/assets/yachiyoc/native/windows-x86_64/menu_video.dll'
$staging = Join-Path $root 'build/native-menu-video'
$compiled = Join-Path $staging 'menu_video.dll'
New-Item -ItemType Directory -Force -Path (Split-Path $output -Parent) | Out-Null
New-Item -ItemType Directory -Force -Path $staging | Out-Null

& $Zig cc -target x86_64-windows-gnu -O3 -s -shared `
    -I (Join-Path $Jdk 'include') -I (Join-Path $Jdk 'include/win32') `
    $source -o $compiled -ld3d11 -lmfplat -lmfreadwrite -lmfuuid -lole32 -luuid -ldxguid
if ($LASTEXITCODE -ne 0) { throw "Native video build failed: $LASTEXITCODE" }
Copy-Item -LiteralPath $compiled -Destination $output -Force
Get-Item -LiteralPath $output | Select-Object Name,Length
