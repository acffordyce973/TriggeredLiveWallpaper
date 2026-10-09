<#
.SYNOPSIS
	Builds the Triggered Wallpaper debug APK, installs it onto the connected ADB device, and launches the app.
#>
param (
	[string]$stringDeviceId = "auto"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir

# 1. Resolve ADB executable path
$fileAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $fileAdb)) {
	$fileAdb = (Get-Command adb -ErrorAction SilentlyContinue).Source
}

if (-not $fileAdb -or -not (Test-Path $fileAdb)) {
	Write-Error "Could not locate adb.exe. Ensure Android platform-tools is installed."
	exit 1
}

# 2. Identify and verify connected target device
Write-Host "Checking connected ADB devices..."
$listRawDevices = & $fileAdb devices -l | Select-String -Pattern "^\s*([^\s]+)\s+device\b"
$listConnectedDevices = @()
foreach ($match in $listRawDevices) {
	if ($match.Line -match "^\s*([^\s]+)\s+device\b") {
		$listConnectedDevices += $matches[1]
	}
}

if ($listConnectedDevices.Count -eq 0) {
	Write-Error "No connected ADB devices detected. Please make sure USB debugging is enabled on your device and the USB cable is plugged in."
	exit 1
}

if ($stringDeviceId -eq "auto" -or [string]::IsNullOrWhiteSpace($stringDeviceId)) {
	# Pick the first non-emulator physical device, or fallback to first available
	$stringTargetDevice = $listConnectedDevices | Where-Object { $_ -notlike "emulator-*" } | Select-Object -First 1
	if (-not $stringTargetDevice) {
		$stringTargetDevice = $listConnectedDevices[0]
	}
	$stringDeviceId = $stringTargetDevice
} else {
	if ($stringDeviceId -notin $listConnectedDevices) {
		Write-Warning "Specified device ID '$stringDeviceId' not found among connected devices: $($listConnectedDevices -join ', ')."
		Write-Host "Attempting fallback to first connected physical device..."
		$stringFallback = $listConnectedDevices | Where-Object { $_ -notlike "emulator-*" } | Select-Object -First 1
		if ($stringFallback) {
			$stringDeviceId = $stringFallback
		} else {
			$stringDeviceId = $listConnectedDevices[0]
		}
	}
}

$stringModel = (& $fileAdb -s $stringDeviceId shell getprop ro.product.model).Trim()
$stringRelease = (& $fileAdb -s $stringDeviceId shell getprop ro.build.version.release).Trim()
$stringSdk = (& $fileAdb -s $stringDeviceId shell getprop ro.build.version.sdk).Trim()

Write-Host "Target Device: $stringModel ($stringDeviceId) - Android $stringRelease (API $stringSdk)"

# 3. Build debug APK
Write-Host "Compiling debug APK..."
Set-Location $projectDir
& "$projectDir\gradlew.bat" assembleDebug
if ($LASTEXITCODE -ne 0) {
	Write-Error "Gradle build failed with exit code $LASTEXITCODE."
	exit $LASTEXITCODE
}

# 4. Install onto target device
$fileApk = "$projectDir\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $fileApk)) {
	Write-Error "Could not find built APK at '$fileApk'."
	exit 1
}

Write-Host "Installing APK onto $stringModel ($stringDeviceId)..."
& $fileAdb -s $stringDeviceId install -r $fileApk
if ($LASTEXITCODE -ne 0) {
	Write-Error "Failed to install APK onto device."
	exit $LASTEXITCODE
}

# 5. Launch MainActivity
Write-Host "Launching Triggered Wallpaper app on $stringModel..."
& $fileAdb -s $stringDeviceId shell am start -n "com.antigravity.triggeredwallpaper/.MainActivity"

Write-Host "Triggered Wallpaper launched successfully on $stringModel ($stringDeviceId)!"
