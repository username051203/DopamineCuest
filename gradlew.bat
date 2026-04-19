@rem Gradle wrapper for Windows (GitHub Actions Windows runners)
@rem
@if "%DEBUG%" == "" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1 || goto error
%JAVA_EXE% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
goto end
:error
echo JAVA_HOME is not set correctly.
exit /b 1
:end
if "%ERRORLEVEL%"=="0" goto mainEnd
exit 1
:mainEnd
if "%OS%"=="Windows_NT" endlocal
