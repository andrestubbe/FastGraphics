@echo off
cd /d "%~dp0"

if not exist "out\FastGraphics.dll" (
    echo FastGraphics.dll nicht gefunden! Baue zuerst:
    echo powershell -ExecutionPolicy Bypass -File build.ps1
    pause
    exit /b 1
)

cd out
java -cp . -Djava.library.path=. demo.DrawOvalTest
