@echo off
rem ---------------------------------------------------------------
rem  Kingdoms + Keystone - launch the game.
rem  Double-click this file. Keep the window open while you play;
rem  it is the game's log, and it is where crashes explain themselves.
rem ---------------------------------------------------------------

setlocal
cd /d "%~dp0"

if not exist "gradlew.bat" (
    echo Could not find gradlew.bat next to this script.
    echo Keep play.bat in the project folder.
    echo.
    pause
    exit /b 1
)

echo.
echo   Kingdoms + Keystone
echo   -------------------
echo   Starting Minecraft. The first run after a code change compiles
echo   first, so give it a moment before the launcher window appears.
echo.

rem Two things matter here. "call" - without it this script hands over
rem control and never comes back, so the error check below never runs. And
rem the full "%~dp0" path - cmd does not search the current directory for
rem programs, so a bare "gradlew.bat" is simply not found.
call "%~dp0gradlew.bat" :neoforge:runClient
set "EXITCODE=%ERRORLEVEL%"

if not "%EXITCODE%"=="0" (
    echo.
    echo   ============================================================
    echo    The game stopped with error code %EXITCODE%.
    echo    Scroll up - the reason is usually the first red line,
    echo    not the last.
    echo   ============================================================
    echo.
    pause
)

endlocal
exit /b %EXITCODE%
