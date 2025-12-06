@echo off
echo ==========================================
echo   Twitch EventSub Mock Server
echo ==========================================
echo.
echo Starting WebSocket server for testing...
echo.
echo After it starts, update TwitchEventSub.java:
echo   EVENTSUB_URL = "ws://127.0.0.1:8080/ws"
echo.
echo Then rebuild the mod and use /twitch connect
echo.
echo Press Ctrl+C to stop the server.
echo.
twitch event websocket start-server
