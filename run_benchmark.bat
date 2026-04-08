@echo off
cd /d "C:\Users\andre\Documents\2026-04-08-Work-FastGraphics\out"

if not exist "FastGraphics.dll" (
    echo.
    echo FEHLER: FastGraphics.dll nicht gefunden!
    echo Bitte zuerst build.ps1 ausfuehren.
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo FastGraphics Benchmark
echo ========================================
echo.
echo Linke Seite:  AWT Graphics2D (Standard Java)
echo Rechte Seite: FastGraphics DirectX
echo.
echo Druecke STRG+C oder schliesse ein Fenster zum Beenden
echo.
echo ========================================
echo.

java -cp . "-Djava.library.path=." demo.BenchmarkApp

pause
