@echo off
setlocal EnableExtensions

set "NO_PAUSE="
if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

set "PROJECT_DIR=%~dp0"
set "DIST_DIR=%PROJECT_DIR%dist"
set "ARTIFACT_ID=bm-minecraft-vip"
set "VERSION=26.3_0.0.1"
set "SOURCE_JAR=%PROJECT_DIR%target\%ARTIFACT_ID%_%VERSION%.jar"
set "MAVEN_REPOSITORY=%PROJECT_DIR%.tools\m2"
set "MAVEN_CMD=%PROJECT_DIR%.tools\apache-maven-3.9.11\bin\mvn.cmd"
set "JDK_HOME="

for /d %%D in ("%PROJECT_DIR%.tools\*") do if exist "%%~fD\bin\javac.exe" set "JDK_HOME=%%~fD"
if not defined JDK_HOME for /f "tokens=2,*" %%A in ('reg query "HKLM\SOFTWARE\JavaSoft\JDK\25" /v JavaHome 2^>nul ^| find "JavaHome"') do set "JDK_HOME=%%B"
if not defined JDK_HOME goto :jdk_not_found
if not exist "%JDK_HOME%\bin\javac.exe" goto :jdk_not_found
set "JAVA_HOME=%JDK_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%MAVEN_CMD%" (
    where mvn.cmd >nul 2>&1 || goto :maven_not_found
    set "MAVEN_CMD=mvn.cmd"
)

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
del /a /f /q "%DIST_DIR%\*" >nul 2>&1
for /d %%D in ("%DIST_DIR%\*") do rd /s /q "%%~fD"

pushd "%PROJECT_DIR%"
for %%L in (zh_TW zh_CN ja_JP en_US) do (
    echo [INFO] Building %%L...
    call "%MAVEN_CMD%" "-Dmaven.repo.local=%MAVEN_REPOSITORY%" "-Ddefault.language=%%L" clean package
    if errorlevel 1 (
        popd
        goto :build_failed
    )
    if not exist "%SOURCE_JAR%" (
        popd
        goto :jar_not_found
    )
    copy /y "%SOURCE_JAR%" "%DIST_DIR%\%ARTIFACT_ID%_%VERSION%-%%L.jar" >nul || (
        popd
        goto :copy_failed
    )
)
copy /y "%DIST_DIR%\%ARTIFACT_ID%_%VERSION%-zh_TW.jar" "%SOURCE_JAR%" >nul || (
    popd
    goto :copy_failed
)
echo [INFO] target jar default language: zh_TW
popd

echo [OK] Build complete: %DIST_DIR%
goto :success

:jdk_not_found
echo [ERROR] JDK 25 was not found in .tools or the Windows registry.
goto :failure

:maven_not_found
echo [ERROR] Maven was not found in .tools or PATH.
goto :failure

:build_failed
echo [ERROR] Maven build failed.
goto :failure

:jar_not_found
echo [ERROR] Built JAR was not found: %SOURCE_JAR%
goto :failure

:copy_failed
echo [ERROR] Could not copy a language-specific JAR to dist.
goto :failure

:failure
if not defined NO_PAUSE pause
endlocal
exit /b 1

:success
if not defined NO_PAUSE pause
endlocal
exit /b 0
