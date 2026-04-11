@echo off
chcp 65001 >nul
cd /d "%~dp0\out"
echo ImageZoomDemo - Screenshot + Sinus Animation
echo.
echo Steuerung:
echo   ESC = Beenden
echo.
java -cp . -Djava.library.path=. demo.ImageZoomDemo
