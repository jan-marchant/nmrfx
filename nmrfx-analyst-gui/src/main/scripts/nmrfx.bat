@echo off

rem nvjp [ script  [ arg ... ] ]
rem 
rem optional environment variables:
rem
rem JAVA_HOME  - directory of JDK/JRE, if not set then 'java' must be found on PATH
rem CLASSPATH  - colon separated list of additional jar files & class directories
rem JAVA_OPTS  - list of JVM options, e.g. "-Xmx256m -Dfoo=bar"
rem


if "%OS%" == "Windows_NT" setlocal

set nvjver=${project.version}
set nvjpmain=org.nmrfx.analyst.gui.NMRAnalystApp
set LOG_CONFIG=-Dlogback.configurationFile=config/logback.xml

set dir=%~dp0

set javaexe=java
set cp="%dir%/lib/nmrfx-analyst-gui-%nvjver%.jar;%dir%lib/*;%dir%plugins/*"
set JAVA_OPTS="--add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED"

set testjava="%dir%jre\bin\java.exe"

if exist %testjava% (
    set javaexe=%testjava%
)


%javaexe%  -mx2048m -cp %cp% %LOG_CONFIG% %JAVA_OPTS% %nvjpmain% %*

