@echo off
echo Building RoyalArms...
call gradle build
echo.
echo Done. Your jar is in build\libs\  (put it in your server's plugins folder)
pause
