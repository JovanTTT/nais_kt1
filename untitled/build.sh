#!/bin/bash

# Build script za InfluxDB Microservice aplikaciju

echo "=========================================="
echo "InfluxDB Microservice - Build Script"
echo "=========================================="

# Boje za ispis
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Provera da li je Docker instaliran
if ! command -v docker &> /dev/null
then
    echo -e "${RED}Docker nije instaliran!${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker je instaliran${NC}"

# Provera da li je Docker Compose instaliran
if ! command -v docker-compose &> /dev/null
then
    echo -e "${RED}Docker Compose nije instaliran!${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker Compose je instaliran${NC}"

# Pitaj korisnika šta da uradi
echo ""
echo "Šta želite da uradite?"
echo "1) Izgradi i pokreni aplikaciju (docker-compose up -d)"
echo "2) Samo izgradi Docker image"
echo "3) Pokreni aplikaciju"
echo "4) Zaustavi aplikaciju (docker-compose down)"
echo "5) Pregled logova"
echo "6) Health check"
echo "7) Čist restart (down -v i up -d)"
echo ""
read -p "Unesite opciju (1-7): " choice

case $choice in
    1)
        echo -e "${YELLOW}Izgrada i pokretanje aplikacije...${NC}"
        docker-compose up -d
        echo -e "${GREEN}✓ Aplikacija je pokrenuta!${NC}"
        echo ""
        echo "Čekaj 30-40 sekundi da se baze inicijalizuju..."
        sleep 10
        echo "Pregled logova:"
        docker-compose logs -f --tail=50
        ;;
    2)
        echo -e "${YELLOW}Izgrada Docker image-a...${NC}"
        docker-compose build
        echo -e "${GREEN}✓ Image je izgrađen!${NC}"
        ;;
    3)
        echo -e "${YELLOW}Pokretanje aplikacije...${NC}"
        docker-compose up -d
        echo -e "${GREEN}✓ Aplikacija je pokrenuta!${NC}"
        docker-compose logs -f --tail=20
        ;;
    4)
        echo -e "${YELLOW}Gašenje aplikacije...${NC}"
        docker-compose down
        echo -e "${GREEN}✓ Aplikacija je zaustavljena!${NC}"
        ;;
    5)
        echo -e "${YELLOW}Prikaz logova:${NC}"
        docker-compose logs -f --tail=100
        ;;
    6)
        echo -e "${YELLOW}Health check...${NC}"
        echo ""
        echo "Microservice:"
        curl -s http://localhost:8080/api/measurements/health | jq . || echo "Status: OFFLINE"
        echo ""
        echo "InfluxDB:"
        curl -s http://localhost:8086/ping || echo "Status: OFFLINE"
        echo ""
        echo "Redis:"
        docker-compose exec redis redis-cli ping || echo "Status: OFFLINE"
        ;;
    7)
        echo -e "${YELLOW}Čist restart...${NC}"
        docker-compose down -v
        sleep 2
        docker-compose up -d
        echo -e "${GREEN}✓ Aplikacija je restartana!${NC}"
        echo ""
        echo "Čekaj 30-40 sekundi da se baze inicijalizuju..."
        sleep 10
        docker-compose logs -f --tail=50
        ;;
    *)
        echo -e "${RED}Nevalidna opcija!${NC}"
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "Za više informacija vidite README.md"
echo "=========================================="
