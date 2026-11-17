@echo off
REM Simplified Gradle wrapper for Windows
where gradle >nul 2>&1
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle not found. Please install Gradle 8.7 or use GitHub Actions to build.
exit /b 1
