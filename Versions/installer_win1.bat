@echo off
echo ============================================
echo    MagmaFlow Professional Installer Build
echo ============================================
echo.

REM Change to project directory
cd /d "%~dp0"

REM Check if Maven is available
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven (mvn) not found in PATH
    echo Please install Apache Maven or add it to your PATH
    echo Download from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

REM Check if Java is available and version
echo Checking Java version...
java -version 2>&1 | findstr "version" > temp_java_version.txt
set /p JAVA_VERSION_LINE=<temp_java_version.txt
echo %JAVA_VERSION_LINE%
del temp_java_version.txt

REM Verify Java version is 17 or higher
java -version 2>&1 | findstr "\"1[7-9]\|\"[2-9][0-9]" >nul
if %ERRORLEVEL% neq 0 (
    echo WARNING: Java 17 or higher is recommended
    echo Your Java version might cause issues
    echo.
)

echo.
echo Building MagmaFlow installer...
echo.

REM Clean previous builds
echo [1/4] Cleaning previous builds...
mvn clean -q

REM Compile and package
echo [2/4] Compiling application...
mvn compile -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Compilation failed
    pause
    exit /b 1
)

REM Create JAR with dependencies
echo [3/4] Packaging application...
mvn package -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Packaging failed
    pause
    exit /b 1
)

REM Create installer
echo [4/4] Creating Windows installer...
mvn org.panteleyev:jpackage-maven-plugin:jpackage -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Installer creation failed
    echo This might be due to:
    echo - Missing Windows SDK
    echo - Missing Visual Studio Build Tools
    echo - JDK without jpackage support
    pause
    exit /b 1
)

echo.
echo ============================================
echo    BUILD SUCCESSFUL!
echo ============================================
echo.
echo Installer created at:
echo   target\installer\MagmaFlow-1.0.0.exe
echo.
echo The installer includes:
echo   - Complete Java runtime
echo   - All required JavaFX modules  
echo   - Professional Windows integration
echo   - Start menu shortcuts
echo   - Automatic uninstaller
echo.

REM Check if installer exists
if exist "target\installer\MagmaFlow-1.0.0.exe" (
    echo File size: 
    for %%A in ("target\installer\MagmaFlow-1.0.0.exe") do echo   %%~zA bytes
    echo.
    echo You can now distribute this single .exe file
    echo It will work on any Windows 10/11 system without requiring Java installation
) else (
    echo ERROR: Installer file not found!
)

echo.
pause