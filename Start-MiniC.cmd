@echo off
setlocal
cd /d "%~dp0"
where javaw >nul 2>&1
if errorlevel 1 (
  echo Java 21 or later is required. Please install a JDK and add it to PATH.
  pause
  exit /b 1
)
if not exist "target\minic-lab.jar" (
  call mvn -B test package
  if errorlevel 1 (
    echo Build failed. Check the Maven output above.
    pause
    exit /b 1
  )
)
start "" javaw -jar "%~dp0target\minic-lab.jar"
