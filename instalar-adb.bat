@echo off
setlocal enabledelayedexpansion
title Instalar QR Screen por USB (ADB)
cd /d "%~dp0"

set APK=%~dp0dist\QR-Screen-1.0.1.apk
if not exist "%APK%" (
  echo No se encontro %APK%
  pause
  exit /b 1
)

REM Busca adb en PATH o en las rutas tipicas del SDK
set "ADB=adb"
where adb >nul 2>nul
if errorlevel 1 (
  set "ADB="
  for %%p in (
    "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    "C:\dev\android-sdk\platform-tools\adb.exe"
    "%ProgramFiles%\Android\Android Studio\platform-tools\adb.exe"
  ) do if not defined ADB if exist "%%~p" set "ADB=%%~p"
)
if not defined ADB (
  echo No se encontro adb.exe. Instala las Platform-Tools del SDK de Android
  echo o descarga https://developer.android.com/studio/releases/platform-tools
  pause
  exit /b 1
)

echo Usando: %ADB%
echo.
echo 1. Activa Opciones de desarrollador y Depuracion USB en el telefono.
echo 2. Conecta el cable y acepta el aviso "Permitir depuracion USB".
echo.
"%ADB%" devices
echo.
pause
echo Instalando %APK% ...
"%ADB%" install -r "%APK%"
echo.
echo Si aparece "INSTALL_FAILED_VERIFICATION_FAILURE" es el verificador de
echo Google Play. Se puede desactivar solo para ADB con:
echo   "%ADB%" shell settings put global verifier_verify_adb_installs 0
echo y volver a ejecutar este script.
echo.
pause
