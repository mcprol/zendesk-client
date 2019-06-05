@echo off
:setlocal

set JAVA_HOME=C:\jdk1.8.0_202
set JAVA=%JAVA_HOME%\bin\java.exe
set CP=..\conf;..\lib\*

%JAVA% -classpath %CP% mcp.kiuwan.zendesk.ZClient %*

:endlocal