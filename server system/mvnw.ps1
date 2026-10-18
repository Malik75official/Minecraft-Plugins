$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$projectRoot = $PSScriptRoot
$propertiesPath = Join-Path $projectRoot ".mvn\wrapper\maven-wrapper.properties"
$distributionLine = Get-Content -LiteralPath $propertiesPath | Where-Object { $_ -match "^distributionUrl=" } | Select-Object -First 1
if (-not $distributionLine) {
    throw "distributionUrl fehlt in maven-wrapper.properties."
}

$distributionUrl = $distributionLine.Substring("distributionUrl=".Length)
$distributionUri = [Uri]$distributionUrl
$archiveName = Split-Path $distributionUri.LocalPath -Leaf
$distributionName = $archiveName -replace "-bin\.zip$", ""
if ($distributionName -eq $archiveName) {
    throw "Die Maven-Distribution muss eine ZIP-Datei mit dem Suffix -bin.zip sein."
}

$cacheRoot = Join-Path $env:USERPROFILE ".m2\wrapper\dists"
$mavenHome = Join-Path $cacheRoot $distributionName
$mavenCommand = Join-Path $mavenHome "bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $mavenCommand)) {
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    $archivePath = Join-Path $env:TEMP $archiveName
    Write-Host "Lade Maven herunter: $distributionUrl"
    Invoke-WebRequest -UseBasicParsing -Uri $distributionUrl -OutFile $archivePath
    Expand-Archive -LiteralPath $archivePath -DestinationPath $cacheRoot -Force
    Remove-Item -LiteralPath $archivePath -Force
}

if (-not (Test-Path -LiteralPath $mavenCommand)) {
    throw "Maven wurde nicht unter '$mavenCommand' gefunden."
}

Push-Location $projectRoot
try {
    & $mavenCommand @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}