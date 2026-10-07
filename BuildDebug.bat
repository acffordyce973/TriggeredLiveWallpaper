@echo off
setlocal
set "batDir=%~dp0"
echo Building Debug APK...
call "%batDir%gradlew.bat" assembleDebug
if errorlevel 1 (
	echo Debug build failed with error %errorlevel%.
	exit /b %errorlevel%
)
echo.
echo ==========================================================
echo  Debug Build Complete!
echo  APK: %batDir%app\build\outputs\apk\debug\app-debug.apk
echo ==========================================================
echo.
endlocal
