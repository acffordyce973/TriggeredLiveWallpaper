<#
.SYNOPSIS
	Ensures the Android emulator is running, installs the debug APK, and launches Triggered Wallpaper.
#>
param (
	[string]$stringAvdName = "Medium_Phone_API_36.1"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir

# 1. Ensure emulator is running
& "$scriptDir\LaunchEmulator.ps1" -stringAvdName $stringAvdName

# 2. Build and install app via Gradle
Write-Host "Building and installing Triggered Wallpaper APK..."
Set-Location $projectDir
& "$projectDir\gradlew.bat" installDebug
if ($LASTEXITCODE -ne 0) {
	Write-Error "Gradle build or installation failed with exit code $LASTEXITCODE."
	exit $LASTEXITCODE
}

# 3. Launch MainActivity
Write-Host "Launching Triggered Wallpaper app on device..."
$fileAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $fileAdb)) {
	$fileAdb = (Get-Command adb -ErrorAction SilentlyContinue).Source
}
& $fileAdb shell am start -n "com.antigravity.triggeredwallpaper/.MainActivity"

Write-Host "Triggered Wallpaper launched successfully on emulator!"
