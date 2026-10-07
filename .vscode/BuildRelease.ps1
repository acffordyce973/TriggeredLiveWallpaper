<#
.SYNOPSIS
	Builds the Triggered Wallpaper signed release APK, cleans up temporary files,
	and deploys the APK to /Content/Android/TriggeredWallpaper.release.apk.
#>
[CmdletBinding()]
param()

$directoryScript = Split-Path -Parent $MyInvocation.MyCommand.Path
$directoryProject = Split-Path -Parent $directoryScript
$directoryContentAndroid = "D:\Personal\Content\Android"
if (-not (Test-Path $directoryContentAndroid)) {
	$directoryContentAndroid = Split-Path -Parent $directoryProject
}
$fileTargetApk = Join-Path $directoryContentAndroid "TriggeredWallpaper.release.apk"
$fileSourceApk = Join-Path $directoryProject "app\build\outputs\apk\release\app-release.apk"

function cleanTempFiles([string]$directoryRoot) {
	Write-Host "Cleaning up stray temporary files and screenshots in '$directoryRoot'..."
	$listStrayPatterns = @(
		"crop.png",
		"dialog_*.png",
		"new_*.png",
		"rules_tab*.png",
		"sets_*.png",
		"time_picker*.png",
		"tp_*.png",
		"wallpaper*.png",
		"dump.xml",
		"screenshot*.png"
	)
	foreach ($stringPattern in $listStrayPatterns) {
		$listMatchingFiles = Get-ChildItem -Path $directoryRoot -Filter $stringPattern -File -ErrorAction SilentlyContinue
		foreach ($fileMatch in $listMatchingFiles) {
			try {
				Remove-Item -Path $fileMatch.FullName -Force -ErrorAction SilentlyContinue
				Write-Host "  Removed: $($fileMatch.Name)" -ForegroundColor DarkGray
			} catch {
				# Ignored
			}
		}
	}
}

# 1. Pre-build cleanup
cleanTempFiles -directoryRoot $directoryProject

# 2. Build Release APK via Gradle
Write-Host "Compiling signed Release APK..." -ForegroundColor Cyan
Push-Location $directoryProject
try {
	& "$directoryProject\gradlew.bat" assembleRelease
	$intExitCode = $LASTEXITCODE
	if ($intExitCode -ne 0) {
		Write-Error "Gradle release build failed with exit code $intExitCode."
		exit $intExitCode
	}
} finally {
	Pop-Location
}

# 3. Verify built APK
if (-not (Test-Path $fileSourceApk)) {
	Write-Error "Could not find built APK at '$fileSourceApk'."
	exit 1
}

# 4. Deploy APK to /Content/Android/TriggeredWallpaper.release.apk
Write-Host "Deploying APK to '$fileTargetApk'..." -ForegroundColor Cyan
if (-not (Test-Path $directoryContentAndroid)) {
	New-Item -ItemType Directory -Path $directoryContentAndroid -Force | Out-Null
}

Copy-Item -Path $fileSourceApk -Destination $fileTargetApk -Force

if (-not (Test-Path $fileTargetApk)) {
	Write-Error "Failed to copy release APK to '$fileTargetApk'."
	exit 1
}

# 5. Post-build cleanup
cleanTempFiles -directoryRoot $directoryProject

# 6. Report success
$objFileInfo = Get-Item $fileTargetApk
$floatSizeMb = [math]::Round($objFileInfo.Length / 1MB, 2)

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " Release Build Complete!" -ForegroundColor Green
Write-Host " APK File: $fileTargetApk" -ForegroundColor White
Write-Host " File Size: $floatSizeMb MB ($($objFileInfo.Length) bytes)" -ForegroundColor White
Write-Host " Last Modified: $($objFileInfo.LastWriteTime)" -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""
