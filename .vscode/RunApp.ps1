<#
.SYNOPSIS
	Ensures the Android emulator is running, installs the debug APK, and launches Triggered Wallpaper.
	Automatically detects installed AVDs or defaults to "Emulated".
#>
param (
	[string]$stringAvdName = "auto"
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

# 3. Launch MainActivity on the emulated device
Write-Host "Launching Triggered Wallpaper app on emulated device..."
$fileAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $fileAdb)) {
	$fileAdb = (Get-Command adb -ErrorAction SilentlyContinue).Source
}

$stringEmulatorDevice = (& $fileAdb devices 2>$null | Select-String "emulator-\d+\s+device" | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1)

if ($stringEmulatorDevice) {
	& $fileAdb -s $stringEmulatorDevice shell am start -n "com.antigravity.triggeredwallpaper/.MainActivity"
} else {
	& $fileAdb shell am start -n "com.antigravity.triggeredwallpaper/.MainActivity"
}

Write-Host "Triggered Wallpaper launched successfully on emulated device!"
