@echo off
call "%~dp0scripts\run.cmd" stop %*
exit /b %errorlevel%
