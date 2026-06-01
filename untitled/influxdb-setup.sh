#!/bin/bash
# InfluxDB Setup Script - Inicijalizacija baze podataka

set -e

echo "InfluxDB setup script se pokušava pokrenuti automatski u docker-compose"
echo "Ako trebate ručnu inicijalizaciju, koristite sledeće komande:"
echo ""
echo "# Pristup InfluxDB shell-u"
echo "docker-compose exec influxdb influx"
echo ""
echo "# Kreiraj org (ako nije već kreiran)"
echo "influx org create -n myorg"
echo ""
echo "# Kreiraj bucket"
echo "influx bucket create -n measurements -o myorg"
echo ""
echo "# Kreiraj token"
echo "influx auth create -n mytoken --read-bucket 00000000-0000-0000-0000-000000000000 --write-bucket 00000000-0000-0000-0000-000000000000 -o myorg"
echo ""
echo "# Prikaži sve buckete"
echo "influx bucket list -o myorg"
echo ""
echo "# Prikaži sve tokene"
echo "influx auth list"
