@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
pushd "%~dp0.." || exit /b 1

where py >nul 2>&1
if not errorlevel 1 (
    py -3 -c "import sys; sys.exit(sys.version_info < (3, 10))" >nul 2>&1
    if not errorlevel 1 goto use_py
)
where python >nul 2>&1
if not errorlevel 1 (
    python -c "import sys; sys.exit(sys.version_info < (3, 10))" >nul 2>&1
    if not errorlevel 1 goto use_python
)
echo Python 3.10+ was not found. Install Python and enable Add Python to PATH.
set "result=1"
goto finish

:use_py
py -3 "%~dp0dev.py" %*
set "result=%errorlevel%"
goto finish

:use_python
python "%~dp0dev.py" %*
set "result=%errorlevel%"

:finish
if not "%result%"=="0" echo Command failed. Read the error above and logs in .runtime.
popd
if not defined CLUB_NO_PAUSE pause
exit /b %result%
