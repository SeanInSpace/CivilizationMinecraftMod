@echo off
rem ---------------------------------------------------------------
rem  Kingdoms + Keystone - run a dedicated server.
rem
rem  Useful for watching a town run without a client attached, and for
rem  testing that both mods load. Type "stop" in this window to shut
rem  down cleanly - closing the window instead can leave the Java
rem  process holding the world lock and port 25565.
rem ---------------------------------------------------------------

setlocal
cd /d "%~dp0"

if not exist "gradlew.bat" (
    echo Could not find gradlew.bat next to this script.
    echo Keep server.bat in the project folder.
    echo.
    pause
    exit /b 1
)

echo.
echo   Kingdoms + Keystone - dedicated server
echo   -------------------------------------
echo   Connect a client to  localhost
echo   Type  stop  here to shut down cleanly.
echo.

call "%~dp0gradlew.bat" :neoforge:runServer
set "EXITCODE=%ERRORLEVEL%"

if not "%EXITCODE%"=="0" (
    echo.
    echo   ============================================================
    echo    The server stopped with error code %EXITCODE%.
    echo    If it says the world is locked or the port is in use, an
    echo    earlier server is still running - end "java.exe" in Task
    echo    Manager, then try again.
    echo   ============================================================
    echo.
    pause
)

endlocal
exit /b %EXITCODE%
