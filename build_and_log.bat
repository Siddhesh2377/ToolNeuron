@echo off
REM Build script for ToolNeuron Android app
cd /d "%~dp0"

echo Starting Android build...
echo.

call .\gradlew.bat build --info 2>&1 | tee build_output.log

echo.
echo Build completed. Check build_output.log for details.
echo.

if %ERRORLEVEL% NEQ 0 (
    echo Build FAILED with error code %ERRORLEVEL%
    echo.
    echo Last 50 lines of error output:
    tail -n 50 build_output.log
) else (
    echo Build SUCCESS!
)

pause
