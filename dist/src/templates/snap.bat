@REM snap launcher script
@REM 
@REM Envioronment:
@REM JAVA_HOME - location of a JDK home dir (optional if java on path)
@REM SBT_OPTS  - JVM options (optional)
@REM Configuration:
@REM snapconfig.txt found in the SNAP_HOME.
@setlocal enabledelayedexpansion

@echo off
if "%SNAP_HOME%"=="" set SNAP_HOME=%~dp0
set ERROR_CODE=0
${{template_declares}}
set SNAP_LAUNCH_JAR=snap-launch-%SNAP_VERSION%.jar

rem Detect if we were double clicked, although theoretically A user could
rem manually run cmd /c
for %%x in (%cmdcmdline%) do if %%~x==/c set DOUBLECLICKED=1

rem FIRST we load the config file of extra options.
set CFG_FILE=%SNAP_HOME%snapconfig.txt
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
for /F %%j in ('%_JAVACMD%') do (if %%~j==Usage: (set JAVAINSTALLED=1))
if not defined JAVAINSTALLED (
  echo.
  echo Java is not installed or can't be found at: 
  echo %_JAVACMD%
  echo.
  echo Please go to http://www.java.com/getjava/ and download
  echo a valid Java Runtime and install before running snap.
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
set JAVA_FRIENDLY_SNAP_HOME=/!SNAP_HOME:\=\\!

"%_JAVACMD%" %_JAVA_OPTS% %SNAP_OPTS% "-Dsnap.home=%JAVA_FRIENDLY_SNAP_HOME%" -jar "%SNAP_HOME%\%SNAP_LAUNCH_JAR%" %CMDS%
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end

@endlocal

exit /B %ERROR_CODE%
