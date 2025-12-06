@echo off
echo Configuring Twitch CLI for testing...
echo.
echo The mock WebSocket server needs a configured CLI.
echo You can use a dummy 30-character secret for local testing only.
echo.
twitch configure -i h3izv07nbz4w7f8u107mr9xglpibay -s 0123456789012345678901234567890
echo.
echo Done! Now run: start-server.bat
