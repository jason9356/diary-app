@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem Prefer a healthy local .venv; if broken/missing, offer setup.bat.

set "PY="
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" -c "import sys" >nul 2>&1
  if not errorlevel 1 (
    set "PY=.venv\Scripts\python.exe"
  )
)

if not defined PY (
  echo.
  echo No usable .venv on this PC ^(normal after clone / switching machines^).
  echo Running setup.bat ...
  echo.
  call "%~dp0setup.bat"
  if errorlevel 1 exit /b 1
  if exist ".venv\Scripts\python.exe" set "PY=.venv\Scripts\python.exe"
)

if not defined PY (
  echo Still no Python venv. Fix setup.bat errors above, then retry.
  pause
  exit /b 1
)

"%PY%" "src\main.py"
if errorlevel 1 (
  echo.
  echo App exited with an error. If imports failed, run setup.bat again.
  pause
)
