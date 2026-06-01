package com.influxdb.app.repository;

import com.influxdb.app.dto.MeasurementPointDTO;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class InfluxDBRepository {

    private static final Logger logger = LoggerFactory.getLogger(InfluxDBRepository.class);

    @Autowired
    private InfluxDBClient influxDBClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    /**
     * Upisuje merenje u InfluxDB
     */
    public MeasurementPointDTO createMeasurementPoint(String measurement, String location,
                                                       double temperature, double humidity, double pressure) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

            String id = UUID.randomUUID().toString();
            Instant now = Instant.now();

            String lineProtocol = String.format(
                "%s,location=%s,id=%s temperature=%.2f,humidity=%.2f,pressure=%.2f %d",
                measurement, location, id, temperature, humidity, pressure, now.toEpochMilli() * 1_000_000
            );

            writeApi.writeRecord(bucket, org, com.influxdb.client.domain.WritePrecision.NS, lineProtocol);

            logger.info("Merenje uspešno upisano: {} sa ID: {}", measurement, id);

            return MeasurementPointDTO.builder()
                    .id(id)
                    .measurement(measurement)
                    .location(location)
                    .temperature(temperature)
                    .humidity(humidity)
                    .pressure(pressure)
                    .timestamp(now)
                    .build();
        } catch (Exception e) {
            logger.error("Greška pri upisu merenja", e);
            throw new RuntimeException("Greška pri upisu merenja: " + e.getMessage(), e);
        }
    }

    /**
     * Upisuje merenje sa eksplicitnom vremenskom oznakom (za podatke iz spoljnog izvora,
     * npr. vremenske prognoze). ID je determinističan po (lokacija, vreme) tako da
     * ponovno povlačenje istog sata prepisuje postojeću tačku umesto da je duplira.
     */
    public void writeMeasurementAt(String measurement, String location,
                                   double temperature, double humidity, double pressure, Instant time) {
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

        String id = escapeTag(location) + "-" + time.getEpochSecond();

        String lineProtocol = String.format(
            java.util.Locale.US,
            "%s,location=%s,id=%s temperature=%.2f,humidity=%.2f,pressure=%.2f %d",
            measurement, escapeTag(location), id,
            temperature, humidity, pressure, time.toEpochMilli() * 1_000_000
        );

        writeApi.writeRecord(bucket, org, com.influxdb.client.domain.WritePrecision.NS, lineProtocol);
    }

    /**
     * Escapuje razmake i zareze u tag vrednostima (npr. "Novi Sad") kako bi
     * line protocol bio validan.
     */
    private String escapeTag(String value) {
        return value.replace(" ", "\\ ").replace(",", "\\,");
    }

    /**
     * Briše merenja po ID-u
     */
    public boolean deleteMeasurementPoint(String id) {
        try {
            String query = String.format(
                "from(bucket:\"%s\") |> range(start: 0) |> filter(fn: (r) => r.id == \"%s\")",
                bucket, id
            );

            // Prvo pronađi vremenske raspon
            List<FluxTable> tables = influxDBClient.getQueryApi().query(query, org);

            if (tables.isEmpty() || tables.get(0).getRecords().isEmpty()) {
                logger.warn("Merenje sa ID {} nije pronađeno", id);
                return false;
            }

            // Pronađi min i max timestamp
            Instant minTime = Instant.MAX;
            Instant maxTime = Instant.MIN;

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Instant time = record.getTime();
                    if (time.isBefore(minTime)) minTime = time;
                    if (time.isAfter(maxTime)) maxTime = time;
                }
            }

            // Obriši (DeleteApi očekuje OffsetDateTime)
            influxDBClient.getDeleteApi().delete(
                    OffsetDateTime.ofInstant(minTime, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(maxTime, ZoneOffset.UTC),
                    String.format("id=\"%s\"", id), bucket, org);

            logger.info("Merenje sa ID {} uspešno obrisano", id);
            return true;
        } catch (Exception e) {
            logger.error("Greška pri brisanju merenja sa ID {}", id, e);
            throw new RuntimeException("Greška pri brisanju merenja: " + e.getMessage(), e);
        }
    }

    /**
     * Pronalazi sva merenja za određenu lokaciju
     */
    public List<MeasurementPointDTO> findByLocation(String location) {
        try {
            String query = String.format(
                "from(bucket:\"%s\") |> range(start: 0) |> filter(fn: (r) => r.location == \"%s\") |> sort(columns: [\"_time\"], desc: true)",
                bucket, location
            );

            return executeQuery(query);
        } catch (Exception e) {
            logger.error("Greška pri pretrazi po lokaciji", e);
            throw new RuntimeException("Greška pri pretrazi: " + e.getMessage(), e);
        }
    }

    /**
     * Kompleksan upit 1: Prosečne temperature po lokaciji za poslednih 24h sa sortiranjem
     */
    public List<MeasurementPointDTO> getAverageTemperatureByLocation() {
        try {
            String query = String.format(
                "from(bucket:\"%s\") " +
                "|> range(start: -24h) " +
                "|> filter(fn: (r) => r._measurement == \"weather\" and r._field == \"temperature\") " +
                "|> group(columns: [\"location\"]) " +
                "|> mean() " +
                "|> sort(columns: [\"_value\"], desc: true)",
                bucket
            );

            return executeQuery(query);
        } catch (Exception e) {
            logger.error("Greška pri izvršavanju kompleksnog upita 1", e);
            throw new RuntimeException("Greška: " + e.getMessage(), e);
        }
    }

    /**
     * Kompleksan upit 2: Broj merenja sa visokom temperaturom po lokaciji
     * (filtriranje po pragu + grupisanje po lokaciji + agregacija count + sortiranje).
     */
    public List<MeasurementPointDTO> getHighTemperatureMeasurements(double threshold) {
        try {
            String query = String.format(
                "from(bucket:\"%s\") " +
                "|> range(start: 0) " +
                "|> filter(fn: (r) => r._measurement == \"weather\") " +
                "|> filter(fn: (r) => r._field == \"temperature\") " +
                "|> filter(fn: (r) => r._value > %.2f) " +
                "|> group(columns: [\"location\"]) " +
                "|> count() " +
                "|> sort(columns: [\"_value\"], desc: true)",
                bucket, threshold
            );

            return executeLocationCountQuery(query);
        } catch (Exception e) {
            logger.error("Greška pri izvršavanju kompleksnog upita 2", e);
            throw new RuntimeException("Greška: " + e.getMessage(), e);
        }
    }

    /**
     * Kompleksan upit 3: Maksimalne vrednosti po 1h intervalu za sve merije, grupisano po lokaciji
     */
    public List<MeasurementPointDTO> getMaxValuesPerHour() {
        try {
            String query = String.format(
                "from(bucket:\"%s\") " +
                "|> range(start: 0) " +
                "|> filter(fn: (r) => r._measurement == \"weather\" and r._field == \"temperature\") " +
                "|> group(columns: [\"_measurement\", \"location\"]) " +
                "|> aggregateWindow(every: 1h, fn: max, createEmpty: false) " +
                "|> group() " +
                "|> sort(columns: [\"_value\"], desc: true)",
                bucket
            );

            return executeQuery(query);
        } catch (Exception e) {
            logger.error("Greška pri izvršavanju kompleksnog upita 3", e);
            throw new RuntimeException("Greška: " + e.getMessage(), e);
        }
    }

    /**
     * Pronalazi sve merenje za dati vremenski raspon
     */
    public List<MeasurementPointDTO> findByTimeRange(Instant from, Instant to) {
        try {
            String query = String.format(
                "from(bucket:\"%s\") " +
                "|> range(start: %s, stop: %s) " +
                "|> sort(columns: [\"_time\"], desc: true)",
                bucket, from, to
            );

            return executeQuery(query);
        } catch (Exception e) {
            logger.error("Greška pri pretrazi po vremenskom rasponu", e);
            throw new RuntimeException("Greška pri pretrazi: " + e.getMessage(), e);
        }
    }

    /**
     * Pronalazi sva merenja za određeni tip merenja
     */
    public List<MeasurementPointDTO> findByMeasurement(String measurement) {
        try {
            String query = String.format(
                "from(bucket:\"%s\") |> range(start: 0) |> filter(fn: (r) => r._measurement == \"%s\") |> sort(columns: [\"_time\"], desc: true)",
                bucket, measurement
            );

            return executeQuery(query);
        } catch (Exception e) {
            logger.error("Greška pri pretrazi po tipu merenja", e);
            throw new RuntimeException("Greška pri pretrazi: " + e.getMessage(), e);
        }
    }

    /**
     * Broji ukupan broj merenja
     */
    public long countMeasurements() {
        try {
            String query = String.format(
                "from(bucket:\"%s\") |> range(start: 0) |> group() |> count() |> sum()",
                bucket
            );

            List<FluxTable> tables = influxDBClient.getQueryApi().query(query, org);

            if (!tables.isEmpty() && !tables.get(0).getRecords().isEmpty()) {
                FluxRecord record = tables.get(0).getRecords().get(0);
                Object value = record.getValue();
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
            }
            return 0L;
        } catch (Exception e) {
            logger.error("Greška pri brojanju merenja", e);
            throw new RuntimeException("Greška pri brojanju: " + e.getMessage(), e);
        }
    }

    /**
     * Mapira rezultate count() po lokaciji (kompleksan upit 2).
     */
    private List<MeasurementPointDTO> executeLocationCountQuery(String query) {
        List<MeasurementPointDTO> results = new ArrayList<>();

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(query, org);

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    MeasurementPointDTO dto = MeasurementPointDTO.builder()
                            .measurement(record.getMeasurement() != null ? record.getMeasurement() : "weather")
                            .build();

                    if (record.getValue() instanceof Number number) {
                        dto.setReadingsAboveThreshold(number.longValue());
                    }

                    record.getValues().forEach((key, val) -> {
                        if ("location".equals(key) && val != null) {
                            dto.setLocation(String.valueOf(val));
                        }
                    });

                    results.add(dto);
                }
            }
        } catch (Exception e) {
            logger.error("Greška pri izvršavanju upita", e);
            throw new RuntimeException("Greška pri izvršavanju upita: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Pomoćna metoda za izvršavanje Flux upita
     */
    private List<MeasurementPointDTO> executeQuery(String query) {
        List<MeasurementPointDTO> results = new ArrayList<>();

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(query, org);

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    MeasurementPointDTO dto = MeasurementPointDTO.builder()
                            .measurement(record.getMeasurement())
                            .timestamp(record.getTime())
                            .build();

                    // Postavi vrednosti na osnovu dostupnih polja
                    if (record.getValue() != null) {
                        String fieldName = record.getField();
                        Object value = record.getValue();

                        if ("temperature".equals(fieldName) && value instanceof Number) {
                            dto.setTemperature(((Number) value).doubleValue());
                        } else if ("humidity".equals(fieldName) && value instanceof Number) {
                            dto.setHumidity(((Number) value).doubleValue());
                        } else if ("pressure".equals(fieldName) && value instanceof Number) {
                            dto.setPressure(((Number) value).doubleValue());
                        } else if (value instanceof Number) {
                            // Agregacioni upiti (mean/max) izbace _field kolonu; rezultat
                            // je tada u _value pa ga tretiramo kao temperaturu.
                            dto.setTemperature(((Number) value).doubleValue());
                        }
                    }

                    // Pronađi tag vrednosti (FluxRecord izlaže kolone preko getValues())
                    record.getValues().forEach((key, val) -> {
                        if (val == null) {
                            return;
                        }
                        if ("location".equals(key)) {
                            dto.setLocation(String.valueOf(val));
                        } else if ("id".equals(key)) {
                            dto.setId(String.valueOf(val));
                        }
                    });

                    results.add(dto);
                }
            }
        } catch (Exception e) {
            logger.error("Greška pri izvršavanju upita", e);
            throw new RuntimeException("Greška pri izvršavanju upita: " + e.getMessage(), e);
        }

        return results;
    }
}
