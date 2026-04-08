# FastGraphics - Build und Run
$ErrorActionPreference = "Stop"

$folder = Split-Path -Parent $MyInvocation.MyCommand.Path

# Prüfe ob DLL existiert, sonst build
if (-not (Test-Path "$folder\out\FastGraphics.dll")) {
    Write-Host "FastGraphics.dll nicht gefunden - baue zuerst..." -ForegroundColor Yellow
    & "$folder\build.ps1"
}

# Prüfe ob .class existiert, sonst kompiliere Java
if (-not (Test-Path "$folder\out\demo\DemoApp.class")) {
    Write-Host "Kompiliere Java..." -ForegroundColor Yellow
    $javaHome = "C:\Program Files\Java\jdk-25"
    & "$javaHome\bin\javac.exe" -d "$folder\out" "$folder\src\demo\DemoApp.java"
    & "$javaHome\bin\javac.exe" -d "$folder\out" "$folder\src\demo\SimpleTest.java"
}

# Starte Demo
Write-Host "Starte DemoApp..." -ForegroundColor Green
cd "$folder\out"
$env:PATH = "C:\Program Files\Java\jdk-25\bin;$env:PATH"
java -cp . -Djava.library.path=. demo.DemoApp
