:setlocal

set CURL=.\curl.exe
set CURL_OPTS=--insecure --user marcos.cacabelos@kiuwan.com/token:UBHop9oVWbJ86OyiOeC8ewCqp9veHUPWneZM9ECu
set ZD_HOST=https://kiuwan.zendesk.com

set ZD_VIEW=360010252959

%CURL% %CURL_OPTS% -X GET -H "Content-Type: application/json" %ZD_HOST%/api/v2/views/%ZD_VIEW%/tickets.json

:endlocal