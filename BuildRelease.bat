@echo off
setlocal
set "batDir=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%batDir%.vscode\BuildRelease.ps1"
endlocal
