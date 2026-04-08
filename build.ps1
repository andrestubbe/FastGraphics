# FastGraphics Build Script (PowerShell)
$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "FastGraphics Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Java 25 finden
$javaHome = "C:\Program Files\Java\jdk-25"
if (-not (Test-Path $javaHome)) {
    Write-Host "FEHLER: Java 25 nicht gefunden unter $javaHome" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $javaHome
Write-Host "Java: $javaHome" -ForegroundColor Green

# Verzeichnisse erstellen
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
New-Item -ItemType Directory -Force -Path "$scriptPath\out" | Out-Null

# Java kompilieren
Write-Host "`n[1/2] Java kompilieren..." -ForegroundColor Yellow
$javaFiles = Get-ChildItem -Path "$scriptPath\src" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
& "$javaHome\bin\javac.exe" -d "$scriptPath\out" $javaFiles
if ($LASTEXITCODE -ne 0) { 
    Write-Host "Java Fehler!" -ForegroundColor Red
    exit 1
}
Write-Host "OK" -ForegroundColor Green

# Visual Studio finden
$vsWhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$vsPath = & $vsWhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath

if (-not $vsPath) {
    Write-Host "`nFEHLER: Visual Studio nicht gefunden!" -ForegroundColor Red
    Write-Host "Installiere VS 2022 mit C++ workload" -ForegroundColor Yellow
    exit 1
}

Write-Host "VS: $vsPath" -ForegroundColor Green

# Native kompilieren
Write-Host "`n[2/2] Native DLL (FastGraphics)..." -ForegroundColor Yellow

Import-Module "$vsPath\VC\Auxiliary\Build\Microsoft.VCToolsVars.ps1" -ErrorAction SilentlyContinue

# Fallback: vcvars64.bat
$vcvars = "$vsPath\VC\Auxiliary\Build\vcvars64.bat"
$nativeSrc = "$scriptPath\native\FastGraphics.cpp"
$outDll = "$scriptPath\out\FastGraphics.dll"
cmd /c "call `"$vcvars`" && cl.exe /LD /MD /O2 /I`"$javaHome\include`" /I`"$javaHome\include\win32`" /Fe`"$outDll`" `"$nativeSrc`" d3d11.lib d3dcompiler.lib user32.lib gdi32.lib 2>&1"

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nFEHLER: Native Build fehlgeschlagen!" -ForegroundColor Red
    exit 1
}

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "BUILD ERFOLGREICH!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "`nStarten mit:" -ForegroundColor Cyan
Write-Host "  cd $scriptPath\out" -ForegroundColor White
Write-Host "  java -cp . -Djava.library.path=. demo.DemoApp" -ForegroundColor White
Write-Host "`nFlüssige 60 FPS Animation mit FastGraphics!" -ForegroundColor Yellow
