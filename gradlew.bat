@echo off
setlocal
set "APP_HOME=%~dp0"
set "CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if not exist "%CLASSPATH%" (
  echo ERROR: missing %CLASSPATH% 1>&2
  exit /b 1
)
if defined JAVA_HOME (
  set "JAVACMD=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVACMD=java.exe"
)
"%JAVACMD%" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
