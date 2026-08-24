param(
    [string] $Version = '1.0.14',
    [string] $Maven = 'mvn.cmd',
    [string] $Jpackage = 'jpackage.exe',
    [string] $InnoSetup = (Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe')
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$inputDir = Join-Path $root 'target\installer-input'
$outputDir = Join-Path $root 'dist'
$packageDir = Join-Path $root ("target\inno-installer-{0}" -f $Version)
$appImageDir = Join-Path $packageDir 'app-image'
$iss = Join-Path $PSScriptRoot 'ts3j-client.iss'
$iconPath = Join-Path $PSScriptRoot 'assets\ts3j-client.ico'

function Remove-GeneratedDirectory([string] $path) {
    $rootFull = ([IO.Path]::GetFullPath($root)).TrimEnd('\') + '\'
    $pathFull = [IO.Path]::GetFullPath($path)
    if (-not $pathFull.StartsWith($rootFull, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a path outside the workspace: $pathFull"
    }
    if (Test-Path -LiteralPath $pathFull) {
        Remove-Item -LiteralPath $pathFull -Recurse -Force
    }
}

if (-not (Test-Path -LiteralPath $iss -PathType Leaf)) {
    throw "No se encontró el script de Inno Setup: $iss"
}
if (-not (Test-Path -LiteralPath $iconPath -PathType Leaf)) {
    throw "No se encontró el icono del instalador: $iconPath"
}
if (-not (Test-Path -LiteralPath $InnoSetup -PathType Leaf)) {
    throw "No se encontró ISCC.exe. Instala Inno Setup 6 o indica -InnoSetup con la ruta al compilador."
}

& $Maven clean verify -q
if ($LASTEXITCODE -ne 0) { throw "Maven terminó con el código $LASTEXITCODE." }

& $Maven dependency:copy-dependencies '-DoutputDirectory=target/installer-input' '-DincludeScope=runtime' -q
if ($LASTEXITCODE -ne 0) { throw "No se pudieron copiar las dependencias." }

Copy-Item -LiteralPath (Join-Path $root 'target\ts3j-1.0.3.jar') `
    -Destination (Join-Path $inputDir 'ts3j-1.0.3.jar') -Force

Remove-GeneratedDirectory $packageDir
New-Item -ItemType Directory -Path $appImageDir, $outputDir -Force | Out-Null

$jpackageArgs = @(
    '--type', 'app-image',
    '--name', 'ts3j-client',
    '--app-version', $Version,
    '--input', $inputDir,
    '--main-jar', 'ts3j-1.0.3.jar',
    '--main-class', 'com.github.manevolent.ts3j.client.AppMain',
    '--description', 'TeamSpeak shared voice session timer',
    '--vendor', 'ts3j',
    '--icon', $iconPath,
    '--dest', $appImageDir
)
& $Jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage terminó con el código $LASTEXITCODE." }

$appSource = Join-Path $appImageDir 'ts3j-client'
if (-not (Test-Path -LiteralPath (Join-Path $appSource 'ts3j-client.exe') -PathType Leaf)) {
    throw "jpackage no creó el ejecutable de la aplicación: $appSource"
}

# jpackage embeds the icon in its launcher but does not retain the ICO as a
# standalone file. Inno Setup uses that file for the shortcut IconFilename, so
# ship it beside the launcher as well; this avoids stale/default shell icons.
$installedIconPath = Join-Path $appSource 'ts3j-client.ico'
Copy-Item -LiteralPath $iconPath -Destination $installedIconPath -Force
if (-not (Test-Path -LiteralPath $installedIconPath -PathType Leaf)) {
    throw "No se pudo incluir el icono de los accesos directos: $installedIconPath"
}

$innoArgs = @(
    "/DAppVersion=$Version",
    "/DAppSource=$appSource",
    "/DOutputDir=$outputDir",
    "/DAppIcon=$iconPath",
    $iss
)
& $InnoSetup @innoArgs
if ($LASTEXITCODE -ne 0) { throw "ISCC.exe terminó con el código $LASTEXITCODE." }

$finalExe = Join-Path $outputDir "ts3j-client-$Version.exe"
if (-not (Test-Path -LiteralPath $finalExe -PathType Leaf)) {
    throw "Inno Setup no creó el instalador esperado: $finalExe"
}

Write-Output "Created $finalExe"
