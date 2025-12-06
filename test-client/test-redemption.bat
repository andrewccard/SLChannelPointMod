@echo off
setlocal

if "%~1"=="" (
    echo Usage: test-redemption.bat "Reward Name" [username]
    echo.
    echo Examples:
    echo   test-redemption.bat "Spawn Creeper"
    echo   test-redemption.bat "Give Diamonds" TestViewer
    exit /b 1
)

set REWARD=%~1
set USER=%~2

if "%USER%"=="" set USER=TestViewer

echo Sending redemption: %REWARD% by %USER%
twitch event trigger channel.channel_points_custom_reward_redemption.add --transport=websocket -r "%REWARD%" -u "%USER%"
