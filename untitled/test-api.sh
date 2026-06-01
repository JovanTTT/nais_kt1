#!/bin/bash

# Test skriptu za InfluxDB Microservice API
# Koristite ovaj script za testiranje svih endpointa

BASE_URL="http://localhost:8080/api"
MEASUREMENT_IDS=()

# Boje za ispis
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}InfluxDB Microservice - API Test Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 1. Health Check
echo -e "${YELLOW}1. Health Check${NC}"
curl -s "$BASE_URL/measurements/health" | jq .
echo ""

# 2. Count existing measurements
echo -e "${YELLOW}2. Count Initial Measurements${NC}"
curl -s "$BASE_URL/measurements/count" | jq .
echo ""

# 3. Create multiple measurements
echo -e "${YELLOW}3. Creating 5 Test Measurements${NC}"
for i in {1..5}; do
    response=$(curl -s -X POST "$BASE_URL/measurements" \
        -H "Content-Type: application/json" \
        -d "{
            \"measurement\": \"temperature_measurement\",
            \"location\": \"Test-Location-$i\",
            \"temperature\": $((20 + RANDOM % 15)),
            \"humidity\": $((40 + RANDOM % 40)),
            \"pressure\": $((1000 + RANDOM % 30))
        }")

    id=$(echo $response | jq -r '.id')
    MEASUREMENT_IDS+=("$id")
    echo "   Created measurement $i with ID: $id"
done
echo ""

# 4. Find by location
echo -e "${YELLOW}4. Find by Location${NC}"
curl -s "$BASE_URL/measurements/location/Test-Location-1" | jq '.count'
echo ""

# 5. Find by measurement type
echo -e "${YELLOW}5. Find by Measurement Type${NC}"
curl -s "$BASE_URL/measurements/type/temperature_measurement" | jq '.count'
echo ""

# 6. Complex Query 1 - Average Temperature by Location
echo -e "${YELLOW}6. Complex Query 1 - Average Temperature by Location${NC}"
curl -s "$BASE_URL/measurements/queries/average-temperature" | jq '.count'
echo ""

# 7. Complex Query 2 - High Temperature Measurements
echo -e "${YELLOW}7. Complex Query 2 - High Temperature (threshold=25)${NC}"
curl -s "$BASE_URL/measurements/queries/high-temperature?threshold=25" | jq '.count'
echo ""

# 8. Complex Query 3 - Max Values Per Hour
echo -e "${YELLOW}8. Complex Query 3 - Max Values Per Hour${NC}"
curl -s "$BASE_URL/measurements/queries/max-per-hour" | jq '.count'
echo ""

# 9. Time Range Query
echo -e "${YELLOW}9. Time Range Query (last 24 hours)${NC}"
from_time=$(date -u -d '24 hours ago' +%Y-%m-%dT%H:%M:%SZ)
to_time=$(date -u +%Y-%m-%dT%H:%M:%SZ)
curl -s "$BASE_URL/measurements/time-range?from=$from_time&to=$to_time" | jq '.count'
echo ""

# 10. Count after insertions
echo -e "${YELLOW}10. Count After Insertions${NC}"
curl -s "$BASE_URL/measurements/count" | jq .
echo ""

# 11. Delete test measurements
echo -e "${YELLOW}11. Delete Test Measurements${NC}"
for id in "${MEASUREMENT_IDS[@]}"; do
    response=$(curl -s -X DELETE "$BASE_URL/measurements/$id")
    echo "   Deleted measurement with ID: $id"
done
echo ""

# 12. Count after deletions
echo -e "${YELLOW}12. Count After Deletions${NC}"
curl -s "$BASE_URL/measurements/count" | jq .
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Test Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
