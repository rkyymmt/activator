@REM builder launcher script
@REM 
@REM Envioronment:
@REM JAVA_HOME - location of a JDK home dir (optional if java on path)
@REM SBT_OPTS  - JVM options (optional)
@REM Configuration:
@REM builderconfig.txt found in the BUILDER_HOME.
@setlocal enabledelayedexpansion

@echo off
if "%BUILDER_HOME%"=="" set BUILDER_HOME=%~dp0
set ERROR_CODE=0
${{template_declares}}
set BUILDER_LAUNCH_JAR=builder-launch-%BUILDER_VERSION%.jar

rem Detect if we were double clicked, although theoretically A user could
rem manually run cmd /c
for %%x in (%cmdcmdline%) do if %%~x==/c set DOUBLECLICKED=1

rem FIRST we load the config file of extra options.
set CFG_FILE=%BUILDER_HOME%builderconfig.txt
set CFG_OPTS=
if exist %CFG_FILE% (
  FOR /F "tokens=* eol=# usebackq delims=" %%i IN ("%CFG_FILE%") DO (
    set DO_NOT_REUSE_ME=%%i
    rem ZOMG (Part #2) WE use !! here to delay the expansion of
    rem CFG_OPTS, otherwise it remains "" for this loop.
    set CFG_OPTS=!CFG_OPTS! !DO_NOT_REUSE_ME!
  )
)

rem We use the value of the JAVACMD environment variable if defined
set _JAVACMD=%JAVACMD%

if "%_JAVACMD%"=="" (
  if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
  )
)

if "%_JAVACMD%"=="" set _JAVACMD=java

rem Detect if this java is ok to use.
for /F %%j in ('"%_JAVACMD%" -version  2^>^&1') do (
  if %%~j==Java set JAVAINSTALLED=1
)
if not defined JAVAINSTALLED (
  echo.
  echo Java is not installed or can't be found at: 
  echo %_JAVACMD%
  echo.
  echo Please go to http://www.java.com/getjava/ and download
  echo a valid Java Runtime and install before running builder.
  if defined DOUBLECLICKED pause
  exit /B 1
)


rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
set _JAVA_OPTS=%JAVA_OPTS%
if "%_JAVA_OPTS%"=="" set _JAVA_OPTS=%CFG_OPTS%

:run

if "%*"=="" (
  if defined DOUBLECLICKED (
    set CMDS="ui"
  ) else set CMD=%*
) else set CMDS=%*

rem We add a / in front, so we get file:///C: instead of file://C:
rem Java considers the later a UNC path.
set JAVA_FRIENDLY_BUILDER_HOME=/!BUILDER_HOME:\=\\!

"%_JAVACMD%" %_JAVA_OPTS% -XX:PermSize=64M -XX:MaxPermSize=256M %BUILDER_OPTS% "-Dbuilder.home=%JAVA_FRIENDLY_BUILDER_HOME%" -jar "%BUILDER_HOME%\%BUILDER_LAUNCH_JAR%" %CMDS%
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end

@endlocal

exit /B %ERROR_CODE%
