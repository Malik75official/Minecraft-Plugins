@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mvnw.ps1" %*
exit /b %ERRORLEVEL%