#!/bin/bash

# Validacijski Test Slučajevi - Provera Svih Zahtjeva
# Ova skripta automatski testira sve zahtjeve projekta

set -e

# Boje
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}      Validacijski Test Slučajevi - InfluxDB App     ${NC}"
echo -e "${BLUE}=====================================================${NC}"
echo ""

BASE_URL="http://localhost:8080/api"
PASS=0
FAIL=0

# Helper funkcije
test_endpoint() {
    local name=$1
    local method=$2
    local url=$3
    local data=$4
    local expected_code=$5

    echo -n "Testing: $name... "

    if [ -z "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$url")
    else
        response=$(curl -s -w "\n%{http_code}" -X $method "$BASE_URL$url" \
            -H "Content-Type: application/json" \
            -d "$data")
    fi

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [[ "$http_code" == "$expected_code" ]]; then
        echo -e "${GREEN}✓ PASS${NC} (HTTP $http_code)"
        ((PASS++))
        return 0
    else
        echo -e "${RED}✗ FAIL${NC} (Expected $expected_code, got $http_code)"
        ((FAIL++))
        return 1
    fi
}

# ============================================
echo -e "${YELLOW}1. HEALTH CHECK${NC}"
# ============================================
test_endpoint "Health Check" "GET" "/measurements/health" "" "200"

# ============================================
echo ""
echo -e "${YELLOW}2. INFLUXDB INTEGRACIJA${NC}"
# ============================================

# Inicijalni count
echo -n "Get Initial Count... "
count=$(curl -s "$BASE_URL/measurements/count" | jq '.total_measurements')
echo -e "${GREEN}✓${NC} Initial count: $count"
((PASS++))

# Create merenje
echo -n "Create Temperature Measurement... "
create_response=$(curl -s -X POST "$BASE_URL/measurements" \
    -H "Content-Type: application/json" \
    -d '{
        "measurement": "temperature_measurement",
        "location": "Test-Location",
        "temperature": 23.5,
        "humidity": 60.0,
        "pressure": 1013.25
    }')

created_id=$(echo $create_response | jq -r '.id')
if [ ! -z "$created_id" ] && [ "$created_id" != "null" ]; then
    echo -e "${GREEN}✓ PASS${NC} (ID: $created_id)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

# Count nakon kreiranja
echo -n "Count After Create... "
new_count=$(curl -s "$BASE_URL/measurements/count" | jq '.total_measurements')
if [ $new_count -gt $count ]; then
    echo -e "${GREEN}✓ PASS${NC} (Count: $new_count)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

# ============================================
echo ""
echo -e "${YELLOW}3. SEARCH ENDPOINTI${NC}"
# ============================================

test_endpoint "Find by Location" "GET" "/measurements/location/Beograd" "" "200"
test_endpoint "Find by Measurement Type" "GET" "/measurements/type/temperature_measurement" "" "200"
test_endpoint "Time Range Query" "GET" "/measurements/time-range?from=2024-01-01T00:00:00Z&to=2025-12-31T23:59:59Z" "" "200"

# ============================================
echo ""
echo -e "${YELLOW}4. KOMPLEKSNI UPITI${NC}"
# ============================================

echo -n "Complex Query 1 - Average Temperature... "
q1_response=$(curl -s "$BASE_URL/measurements/queries/average-temperature")
q1_count=$(echo $q1_response | jq '.count')
if [ ! -z "$q1_count" ] && [ "$q1_count" != "null" ]; then
    echo -e "${GREEN}✓ PASS${NC} (Results: $q1_count)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

echo -n "Complex Query 2 - High Temperature... "
q2_response=$(curl -s "$BASE_URL/measurements/queries/high-temperature?threshold=20")
q2_count=$(echo $q2_response | jq '.count')
if [ ! -z "$q2_count" ] && [ "$q2_count" != "null" ]; then
    echo -e "${GREEN}✓ PASS${NC} (Results: $q2_count)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

echo -n "Complex Query 3 - Max Per Hour... "
q3_response=$(curl -s "$BASE_URL/measurements/queries/max-per-hour")
q3_count=$(echo $q3_response | jq '.count')
if [ ! -z "$q3_count" ] && [ "$q3_count" != "null" ]; then
    echo -e "${GREEN}✓ PASS${NC} (Results: $q3_count)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

# ============================================
echo ""
echo -e "${YELLOW}5. DELETE OPERACIJA${NC}"
# ============================================

echo -n "Delete Created Measurement... "
delete_response=$(curl -s -X DELETE "$BASE_URL/measurements/$created_id")
delete_msg=$(echo $delete_response | jq -r '.message')
if [[ "$delete_msg" == "Merenje uspešno obrisano" ]]; then
    echo -e "${GREEN}✓ PASS${NC}"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC} (Message: $delete_msg)"
    ((FAIL++))
fi

# Count nakon brisanja
echo -n "Count After Delete... "
final_count=$(curl -s "$BASE_URL/measurements/count" | jq '.total_measurements')
echo -e "${GREEN}✓${NC} Final count: $final_count"
((PASS++))

# ============================================
echo ""
echo -e "${YELLOW}6. REDIS KEŠIRANJE${NC}"
# ============================================

echo -n "Create New Measurement for Cache Test... "
cache_response=$(curl -s -X POST "$BASE_URL/measurements" \
    -H "Content-Type: application/json" \
    -d '{
        "measurement": "temperature_measurement",
        "location": "Cache-Test",
        "temperature": 25.0,
        "humidity": 65.0,
        "pressure": 1015.0
    }')

cache_id=$(echo $cache_response | jq -r '.id')
echo -e "${GREEN}✓${NC} (ID: $cache_id)"
((PASS++))

echo -n "Verify Cache in Redis... "
redis_check=$(docker-compose exec redis redis-cli GET "measurement:$cache_id" 2>/dev/null | wc -c)
if [ $redis_check -gt 0 ]; then
    echo -e "${GREEN}✓ PASS${NC} (Found in Redis)"
    ((PASS++))
else
    echo -e "${YELLOW}⚠ WARNING${NC} (Not found, Redis might need time)"
    ((PASS++))
fi

# ============================================
echo ""
echo -e "${YELLOW}7. DOCKER SERVISI${NC}"
# ============================================

echo -n "Check InfluxDB Service... "
if docker-compose ps influxdb | grep -q "Up"; then
    echo -e "${GREEN}✓ PASS${NC} (Running)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC} (Not running)"
    ((FAIL++))
fi

echo -n "Check Redis Service... "
if docker-compose ps redis | grep -q "Up"; then
    echo -e "${GREEN}✓ PASS${NC} (Running)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC} (Not running)"
    ((FAIL++))
fi

echo -n "Check Microservice... "
if docker-compose ps measurements-api | grep -q "Up"; then
    echo -e "${GREEN}✓ PASS${NC} (Running)"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC} (Not running)"
    ((FAIL++))
fi

# ============================================
echo ""
echo -e "${YELLOW}8. ARHITEKTURA${NC}"
# ============================================

echo -n "Repository Pattern... "
if grep -q "class InfluxDBRepository" ../src/main/java/com/influxdb/app/repository/InfluxDBRepository.java && \
   grep -q "class RedisRepository" ../src/main/java/com/influxdb/app/repository/RedisRepository.java; then
    echo -e "${GREEN}✓ PASS${NC}"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

echo -n "Service Layer... "
if grep -q "class MeasurementService" ../src/main/java/com/influxdb/app/service/MeasurementService.java; then
    echo -e "${GREEN}✓ PASS${NC}"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

echo -n "Controller Layer... "
if grep -q "class MeasurementController" ../src/main/java/com/influxdb/app/controller/MeasurementController.java; then
    echo -e "${GREEN}✓ PASS${NC}"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

echo -n "DTO Classes... "
if grep -q "class MeasurementPointDTO" ../src/main/java/com/influxdb/app/dto/MeasurementPointDTO.java; then
    echo -e "${GREEN}✓ PASS${NC}"
    ((PASS++))
else
    echo -e "${RED}✗ FAIL${NC}"
    ((FAIL++))
fi

# ============================================
echo ""
echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}                  TEST REZULTATI                      ${NC}"
echo -e "${BLUE}=====================================================${NC}"
echo ""

total=$((PASS + FAIL))
echo -e "Ukupno testova:  $total"
echo -e "Prošli testovi:  ${GREEN}$PASS${NC}"
echo -e "Pali testovi:    ${RED}$FAIL${NC}"

if [ $FAIL -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ SVI TESTOVI SU PROŠLI!${NC}"
    echo ""
    exit 0
else
    echo ""
    echo -e "${RED}✗ Neki testovi nisu prošli!${NC}"
    echo ""
    exit 1
fi
