@echo off

REM IMPORTANT: Before running this script, open the Dockerfile and replace 'your_vnc_password' with a strong password.
echo Building Docker image...
docker build -t ui-test-automation-agent -f deployment/local/Dockerfile .

IF %ERRORLEVEL% NEQ 0 (
    echo Docker image build failed. Exiting.
    goto :eof
)

echo Stopping and removing any existing container named 'ui-agent'...
docker stop ui-agent >nul 2>&1
docker rm ui-agent >nul 2>&1

echo Running Docker container...
docker run -d -p 5901:5901 -p 6901:6901 -p 8005:8005 -e VNC_PW=123456 -e VNC_RESOLUTION=1920x1080 -e PORT=8005 -e AGENT_HOST=0.0.0.0 --shm-size=4g --name ui-agent ui-test-automation-agent /app/java_app_startup.sh

IF %ERRORLEVEL% NEQ 0 (
    echo Docker container failed to start. Exiting.
        goto :eof
)

echo Docker container 'ui-agent' is running.
echo You can access the VNC session via a VNC client at localhost:5901
echo Or via your web browser (NoVNC) at http://localhost:6901/vnc.html
echo Remember to use the password you set in the Dockerfile.

pause
