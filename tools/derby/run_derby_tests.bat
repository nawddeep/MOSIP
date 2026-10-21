@echo off
setlocal enabledelayedexpansion

echo ================================================================================
echo    MOSIP Desktop Face Liveness - Derby Database Automated Verification
echo ================================================================================

cd /d "%~dp0\..\.."

if not exist "tools\derby\derby.jar" (
    echo [ERROR] derby.jar not found in tools\derby. Downloading from Maven Central...
    curl -s -L -o "tools\derby\derby.jar" "https://repo1.maven.org/maven2/org/apache/derby/derby/10.14.2.0/derby-10.14.2.0.jar"
)

if not exist "tools\derby\gson.jar" (
    echo [ERROR] gson.jar not found in tools\derby. Downloading from Maven Central...
    curl -s -L -o "tools\derby\gson.jar" "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
)

if not exist "tools\derby\classes" mkdir "tools\derby\classes"

echo [BUILD] Compiling Java classes...
javac -cp "tools/derby/derby.jar;tools/derby/gson.jar" -d "tools/derby/classes" @tools/derby/sources.txt
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [RUN] Running Derby Verification Harness...
java -cp "tools/derby/classes;tools/derby/derby.jar;tools/derby/gson.jar" tools.derby.TestDerbyIntegration
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Test execution failed!
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Derby Database tests completed successfully!
