@echo off
:: FastGraphics Native DLL Compiler Script
echo ========================================
echo FastGraphics Native Library Builder
echo ========================================

set LIB_NAME=fastgraphics

:: Try to find VS
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "%VSWHERE%" (
    for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do (
        set "VS_PATH=%%i"
    )
)

if not defined VS_PATH (
    echo ERROR: Visual Studio not found!
    exit /b 1
)

:: Find JAVA_HOME
if not defined JAVA_HOME (
    for /f "tokens=2*" %%a in ('reg query "HKLM\SOFTWARE\JavaSoft\JDK" /v "CurrentVersion" 2^>nul') do set "JDK_VER=%%b"
    for /f "tokens=2*" %%a in ('reg query "HKLM\SOFTWARE\JavaSoft\JDK\%JDK_VER%" /v "JavaHome" 2^>nul') do set "JAVA_HOME=%%b"
)

if not defined JAVA_HOME (
    echo ERROR: JAVA_HOME not set!
    exit /b 1
)

call "%VS_PATH%\VC\Auxiliary\Build\vcvars64.bat"

if not exist build mkdir build

:: Compile C++ source
cl.exe /O2 /W3 /MD /EHsc /LD ^
   /I "%JAVA_HOME%\include" ^
   /I "%JAVA_HOME%\include\win32" ^
   /Fo:build\ ^
   /Fe:build\%LIB_NAME%.dll ^
   native\*.cpp ^
   user32.lib gdi32.lib d3d11.lib d3dcompiler.lib d2d1.lib dwrite.lib ^
   /link /DLL /MACHINE:X64

if %ERRORLEVEL% == 0 (
    echo [SUCCESS] DLL built at: build\%LIB_NAME%.dll
) else (
    echo [FAILED] Compilation failed.
    exit /b 1
)
pause
