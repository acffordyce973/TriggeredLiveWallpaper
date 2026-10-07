<#
.SYNOPSIS
	Launches the Android emulator and waits until the device is online and ready.
#>
param (
	[string]$stringAvdName = "Medium_Phone_API_36.1"
)

function getEmulatorPath {
	$fileDefault = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
	if (Test-Path $fileDefault) {
		return $fileDefault
	}
	$cmd = Get-Command emulator -ErrorAction SilentlyContinue
	if ($cmd) {
		return $cmd.Source
	}
	return $null
}

function getAdbPath {
	$fileDefault = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
	if (Test-Path $fileDefault) {
		return $fileDefault
	}
	$cmd = Get-Command adb -ErrorAction SilentlyContinue
	if ($cmd) {
		return $cmd.Source
	}
	return $null
}

$fileEmulator = getEmulatorPath
$fileAdb = getAdbPath

if (-not $fileEmulator) {
	Write-Error "Could not find Android emulator.exe in '$env:LOCALAPPDATA\Android\Sdk\emulator' or in PATH."
	exit 1
}

Write-Host "Checking for running Android emulator or device..."
$listDevices = & $fileAdb devices 2>$null
$boolAlreadyRunning = $false
foreach ($stringLine in $listDevices) {
	if ($stringLine -match "emulator-\d+\s+device") {
		$boolAlreadyRunning = $true
		break
	}
}

if ($boolAlreadyRunning) {
	Write-Host "Android emulator is already running and ready."
	exit 0
}

Write-Host "Launching Android emulator '$stringAvdName'..."
$procEmulator = Start-Process -FilePath $fileEmulator -ArgumentList "-avd", $stringAvdName -PassThru

Write-Host "Waiting for Android emulator device to come online..."
& $fileAdb wait-for-device

Write-Host "Waiting for device boot to complete..."
$intAttempts = 0
while ($intAttempts -lt 60) {
	$stringBoot = & $fileAdb shell getprop sys.boot_completed 2>$null
	if ($stringBoot -and $stringBoot.Trim() -eq "1") {
		break
	}
	Start-Sleep -Seconds 2
	$intAttempts++
}

Write-Host "Android emulator '$stringAvdName' is ready!"
