@echo off
setlocal
cd /d "%~dp0"

if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" "src\main.py"
) else (
  python "src\main.py"
)

if errorlevel 1 (
  echo.
  echo Failed to start. Install deps: pip install -r requirements.txt
  pause
)
