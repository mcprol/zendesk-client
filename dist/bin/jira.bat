:setlocal

set CURL=.\curl.exe
set CURL_OPTS=--insecure --basic --user marcos.cacabelos:optimyth
set JIRA_HOST=http://appsval.optimyth.com/jira
set JIRA_ISSUE=SAS-4425


%CURL% %CURL_OPTS% -X GET -H "Content-Type: application/json" %JIRA_HOST%/rest/api/2/issue/%JIRA_ISSUE%

:endlocal