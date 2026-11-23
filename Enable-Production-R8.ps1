$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$appGradle = Join-Path $root "app\build.gradle.kts"

if (-not (Test-Path $appGradle)) {
    Write-Error "Could not find $appGradle. Run this script from the root of your Android project."
    exit 1
}

$backup = "$appGradle.bak"
Copy-Item -Path $appGradle -Destination $backup -Force
Write-Host "Backed up app\build.gradle.kts to app\build.gradle.kts.bak" -ForegroundColor Cyan

$src = [System.IO.File]::ReadAllText($appGradle, [System.Text.Encoding]::UTF8)

if ($src -match "isMinifyEnabled\s*=\s*false") {
    $src = [regex]::Replace($src, "isMinifyEnabled\s*=\s*false", "isMinifyEnabled = true`r`n            isShrinkResources = true")
    Write-Host "Enabled isMinifyEnabled = true and isShrinkResources = true in release buildType." -ForegroundColor Green
} elseif ($src -notmatch "isMinifyEnabled\s*=\s*true") {
    Write-Host "Note: Could not find an existing 'isMinifyEnabled = false' line in app\build.gradle.kts." -ForegroundColor Yellow
    Write-Host "Please verify your release { ... } block manually using README_PRODUCTION_BUILD.md." -ForegroundColor Yellow
} else {
    Write-Host "isMinifyEnabled = true is already enabled in app\build.gradle.kts." -ForegroundColor Green
}

if ($src -notmatch "proguard-android-optimize\.txt") {
    $src = $src -replace "proguard-android\.txt", "proguard-android-optimize.txt"
    Write-Host "Upgraded ProGuard base config to proguard-android-optimize.txt." -ForegroundColor Green
}

[System.IO.File]::WriteAllText($appGradle, $src, [System.Text.Encoding]::UTF8)
Write-Host "`nNext step: Run '.\gradlew.bat clean testDebugUnitTest assembleRelease --configuration-cache'" -ForegroundColor Cyan
