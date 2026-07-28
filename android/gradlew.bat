@ECHO OFF
SETLOCAL
IF NOT DEFINED JAVA_HOME SET JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
IF NOT DEFINED ANDROID_HOME SET ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
SET ANDROID_SDK_ROOT=%ANDROID_HOME%

SET WRAPPER_JAR=%~dp0gradle\wrapper\gradle-wrapper.jar
IF EXIST "%WRAPPER_JAR%" (
  FOR %%A IN ("%WRAPPER_JAR%") DO (
    IF %%~zA GTR 10000 (
      "%JAVA_HOME%\bin\java.exe" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
      EXIT /B %ERRORLEVEL%
    )
  )
)

IF EXIST "%USERPROFILE%\.gradle-dist\gradle-8.9\bin\gradle.bat" (
  "%USERPROFILE%\.gradle-dist\gradle-8.9\bin\gradle.bat" %*
  EXIT /B %ERRORLEVEL%
)

ECHO Missing usable gradle-wrapper.jar. Re-download wrapper or install Gradle 8.9.
EXIT /B 1
