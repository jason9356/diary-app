@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem Portable bootstrap: recreate .venv on this machine and install deps.
rem Safe to re-run after git pull or switching PCs.

where python >nul 2>&1
if errorlevel 1 (
  echo Python not found on PATH.
  echo Install Python 3.10+ from https://www.python.org/downloads/
  echo and check "Add python.exe to PATH", then re-run setup.bat
  pause
  exit /b 1
)

python -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)" >nul 2>&1
if errorlevel 1 (
  echo Need Python 3.10+. Current:
  python --version
  pause
  exit /b 1
)

set "NEED_VENV=0"
if not exist ".venv\Scripts\python.exe" set "NEED_VENV=1"
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" -c "import sys" >nul 2>&1
  if errorlevel 1 set "NEED_VENV=1"
)

if "%NEED_VENV%"=="1" (
  echo Recreating .venv for this machine...
  if exist ".venv" rmdir /s /q ".venv"
  python -m venv .venv
  if errorlevel 1 (
    echo Failed to create .venv
    pause
    exit /b 1
  )
)

echo Installing requirements...
".venv\Scripts\python.exe" -m pip install --upgrade pip
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
  echo Default PyPI failed, trying Tsinghua mirror...
  ".venv\Scripts\python.exe" -m pip install -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt
)
if errorlevel 1 (
  echo pip failed. Install manually, then re-run setup.bat
  pause
  exit /b 1
)

echo.
echo Setup OK. Run: run.bat   or   .venv\Scripts\python.exe src\main.py
exit /b 0
