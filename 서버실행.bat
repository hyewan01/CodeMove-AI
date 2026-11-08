@echo off
cd /d "%~dp0"
chcp 65001 >nul
title CodeMove server

rem ==================================================
rem 더블클릭으로 서버를 켜고 브라우저에서 첫 화면(http://localhost:8080)을 엽니다.
rem 이 창을 닫으면 서버도 꺼집니다.
rem ==================================================

if not exist "src\main\resources\application-local.yml" (
	echo [안내] src\main\resources\application-local.yml 파일이 없어요.
	echo        application-local.yml.example 을 복사해 DB 접속 정보를 넣은 뒤 다시 실행해 주세요.
	pause
	exit /b 1
)

rem Java 21 이 JAVA_HOME 에 없으면 기본 설치 위치를 사용
if "%JAVA_HOME%"=="" if exist "C:\Program Files\Java\jdk-21" set "JAVA_HOME=C:\Program Files\Java\jdk-21"

rem 이미 켜져 있으면 브라우저만 열기
powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient('localhost', 8080); $c.Close(); exit 0 } catch { exit 1 }"
if %errorlevel%==0 (
	echo 서버가 이미 켜져 있어요. 브라우저를 엽니다.
	start "" "http://localhost:8080"
	exit /b 0
)

echo 서버를 켜는 중이에요. 처음에는 1~2분쯤 걸려요.
echo 준비되면 브라우저가 자동으로 열려요. 이 창은 닫지 마세요.
echo.

rem 서버가 준비되면(8080 포트가 열리면) 브라우저 열기
start "" /b powershell -NoProfile -WindowStyle Hidden -Command "for ($i = 0; $i -lt 300; $i++) { try { $c = New-Object Net.Sockets.TcpClient('localhost', 8080); $c.Close(); Start-Process 'http://localhost:8080'; break } catch { Start-Sleep -Seconds 2 } }"

call "%~dp0gradlew.bat" bootRun
pause
