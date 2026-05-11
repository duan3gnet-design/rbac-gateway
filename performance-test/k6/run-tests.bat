@echo off
REM ============================================================================
REM run-tests.bat — Script chạy k6 performance tests (Windows)
REM
REM Usage:
REM   run-tests.bat [scenario] [profile]
REM
REM Ví dụ:
REM   run-tests.bat
REM   run-tests.bat full-system STRESS
REM   run-tests.bat rate-limit
REM   run-tests.bat all
REM ============================================================================

setlocal

set SCENARIO=%~1
set PROFILE=%~2

if "%SCENARIO%"=="" set SCENARIO=gateway-routing
if "%PROFILE%"==""   set PROFILE=LOAD

set BASE_URL=%BASE_URL%
if "%BASE_URL%"==""      set BASE_URL=http://localhost:8080

set ADMIN_USERNAME=%ADMIN_USERNAME%
if "%ADMIN_USERNAME%"=="" set ADMIN_USERNAME=perfAdmin

set ADMIN_PASSWORD=%ADMIN_PASSWORD%
if "%ADMIN_PASSWORD%"=="" set ADMIN_PASSWORD=Admin@123

set USER_USERNAME=%USER_USERNAME%
if "%USER_USERNAME%"==""    set USER_USERNAME=perfUser

set USER_PASSWORD=%USER_PASSWORD%
if "%USER_PASSWORD%"=="" set USER_PASSWORD=User@123

set SCRIPT_DIR=%~dp0
set RESULTS_DIR=%SCRIPT_DIR%results

if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"

REM Tạo timestamp
for /f "tokens=1-4 delims=/ " %%a in ('date /t') do set TODAY=%%d%%b%%c
for /f "tokens=1-2 delims=: " %%a in ('time /t') do set NOW=%%a%%b
set TIMESTAMP=%TODAY%_%NOW%

echo.
echo ================================================
echo    RBAC Gateway -- k6 Performance Tests
echo ================================================
echo.

where k6 >nul 2>&1
if errorlevel 1 (
    echo [ERR] k6 chua duoc cai. Xem: https://k6.io/docs/getting-started/installation/
    echo.
    echo Cai nhanh: scoop install k6
    exit /b 1
)

if "%SCENARIO%"=="all" (
    echo [k6] Chay tat ca scenarios...
    call :run_scenario gateway-routing        SMOKE
    call :run_scenario rbac-auth              LOAD
    call :run_scenario rate-limit             LOAD
    call :run_scenario circuit-breaker        LOAD
    call :run_scenario admin-route-management LOAD
    call :run_scenario full-system            STRESS
    goto :eof
)

call :run_scenario %SCENARIO% %PROFILE%
goto :eof

:run_scenario
set _SCENARIO=%~1
set _PROFILE=%~2
set _SCRIPT=%SCRIPT_DIR%scenarios\%_SCENARIO%.js
set _OUTPUT=%RESULTS_DIR%\%_SCENARIO%_%TIMESTAMP%.json

if not exist "%_SCRIPT%" (
    echo [ERR] Khong tim thay: %_SCRIPT%
    exit /b 1
)

echo [k6] Chay: %_SCENARIO% [profile=%_PROFILE%]
echo   Script  : %_SCRIPT%
echo   Base URL: %BASE_URL%
echo   Output  : %_OUTPUT%
echo.

k6 run ^
    --env BASE_URL=%BASE_URL% ^
    --env TEST_PROFILE=%_PROFILE% ^
    --env ADMIN_USERNAME=%ADMIN_USERNAME% ^
    --env ADMIN_PASSWORD=%ADMIN_PASSWORD% ^
    --env USER_USERNAME=%USER_USERNAME% ^
    --env USER_PASSWORD=%USER_PASSWORD% ^
    --out json="%_OUTPUT%" ^
    "%_SCRIPT%"

if errorlevel 1 (
    echo [ERR] Scenario '%_SCENARIO%' FAILED
) else (
    echo [OK]  Scenario '%_SCENARIO%' PASSED
)
echo.
goto :eof
