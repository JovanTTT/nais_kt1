@echo off
REM Build script za InfluxDB Microservice aplikaciju (Windows)

setlocal enabledelayedexpansion

echo.
echo ==========================================
echo InfluxDB Microservice - Build Script
echo ==========================================
echo.

REM Provera da li je Docker instaliran
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker nije instaliran!
    pause
    exit /b 1
)
echo [OK] Docker je instaliran

REM Provera da li je Docker Compose instaliran
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose nije instaliran!
    pause
    exit /b 1
)
echo [OK] Docker Compose je instaliran

echo.
echo Sta zelite da uradite?
echo 1) Izgradi i pokreni aplikaciju (docker-compose up -d)
echo 2) Samo izgradi Docker image
echo 3) Pokreni aplikaciju
echo 4) Zaustavi aplikaciju (docker-compose down)
echo 5) Pregled logova
echo 6) Health check
echo 7) Cist restart (down -v i up -d)
echo.
set /p choice="Unesite opciju (1-7): "

if "%choice%"=="1" (
    echo.
    echo [INFO] Izgrada i pokretanje aplikacije...
    docker-compose up -d
    echo [OK] Aplikacija je pokrenuta!
    echo.
    echo Cekaj 30-40 sekundi da se baze inicijalizuju...
    timeout /t 10
    echo.
    echo [INFO] Pregled logova:
    docker-compose logs -f --tail=50
) else if "%choice%"=="2" (
    echo.
    echo [INFO] Izgrada Docker image-a...
    docker-compose build
    echo [OK] Image je izgraden!
) else if "%choice%"=="3" (
    echo.
    echo [INFO] Pokretanje aplikacije...
    docker-compose up -d
    echo [OK] Aplikacija je pokrenuta!
    docker-compose logs -f --tail=20
) else if "%choice%"=="4" (
    echo.
    echo [INFO] Gasenje aplikacije...
    docker-compose down
    echo [OK] Aplikacija je zaustavljena!
) else if "%choice%"=="5" (
    echo.
    echo [INFO] Prikaz logova:
    docker-compose logs -f --tail=100
) else if "%choice%"=="6" (
    echo.
    echo [INFO] Health check...
    echo.
    echo Microservice:
    curl -s http://localhost:8080/api/measurements/health
    echo.
    echo InfluxDB:
    curl -s http://localhost:8086/ping
    echo.
    echo Redis:
    docker-compose exec redis redis-cli ping
    echo.
) else if "%choice%"=="7" (
    echo.
    echo [INFO] Cist restart...
    docker-compose down -v
    timeout /t 2
    docker-compose up -d
    echo [OK] Aplikacija je restartana!
    echo.
    echo Cekaj 30-40 sekundi da se baze inicijalizuju...
    timeout /t 10
    docker-compose logs -f --tail=50
) else (
    echo [ERROR] Nevalidna opcija!
    pause
    exit /b 1
)

echo.
echo ==========================================
echo Za vise informacija vidite README.md
echo ==========================================
echo.
pause
