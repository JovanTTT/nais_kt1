package com.influxdb.app.controller;

import com.influxdb.app.dto.CreateMeasurementPointDTO;
import com.influxdb.app.dto.MeasurementPointDTO;
import com.influxdb.app.dto.QueryResultDTO;
import com.influxdb.app.service.MeasurementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/measurements")
@CrossOrigin(origins = "*")
public class MeasurementController {

    private static final Logger logger = LoggerFactory.getLogger(MeasurementController.class);

    @Autowired
    private MeasurementService measurementService;

    /**
     * Kreiraj novo merenje
     * POST /api/measurements
     */
    @PostMapping
    public ResponseEntity<MeasurementPointDTO> createMeasurement(@RequestBody CreateMeasurementPointDTO dto) {
        logger.info("POST /measurements - Kreiranje novog merenja");

        try {
            MeasurementPointDTO created = measurementService.createMeasurement(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            logger.error("Greška pri kreiranju merenja", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obriši merenje po ID-u
     * DELETE /api/measurements/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteMeasurement(@PathVariable String id) {
        logger.info("DELETE /measurements/{} - Brisanje merenja", id);

        try {
            boolean deleted = measurementService.deleteMeasurement(id);

            Map<String, String> response = new HashMap<>();
            if (deleted) {
                response.put("message", "Merenje uspešno obrisano");
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Merenje nije pronađeno");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (Exception e) {
            logger.error("Greška pri brisanju merenja", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Pronađi merenja po lokaciji
     * GET /api/measurements/location/{location}
     */
    @GetMapping("/location/{location}")
    public ResponseEntity<QueryResultDTO> getByLocation(@PathVariable String location) {
        logger.info("GET /measurements/location/{} - Pronalaženje merenja po lokaciji", location);

        try {
            QueryResultDTO result = measurementService.findByLocation(location);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Greška pri pretrazi po lokaciji", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Pronađi merenja po tipu merenja
     * GET /api/measurements/type/{measurement}
     */
    @GetMapping("/type/{measurement}")
    public ResponseEntity<QueryResultDTO> getByMeasurement(@PathVariable String measurement) {
        logger.info("GET /measurements/type/{} - Pronalaženje merenja po tipu", measurement);

        try {
            QueryResultDTO result = measurementService.findByMeasurement(measurement);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Greška pri pretrazi po tipu", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Pronađi merenja u vremenskom rasponu
     * GET /api/measurements/time-range?from=2024-01-01T00:00:00Z&to=2024-01-02T00:00:00Z
     */
    @GetMapping("/time-range")
    public ResponseEntity<QueryResultDTO> getByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        logger.info("GET /measurements/time-range - Pronalaženje merenja u rasponu od {} do {}", from, to);

        try {
            QueryResultDTO result = measurementService.findByTimeRange(from, to);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Greška pri pretrazi po vremenskom rasponu", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Kompleksan upit 1: Prosečne temperature po lokaciji
     * GET /api/measurements/queries/average-temperature
     */
    @GetMapping("/queries/average-temperature")
    public ResponseEntity<QueryResultDTO> getAverageTemperature() {
        logger.info("GET /measurements/queries/average-temperature - Izvršavanje kompleksnog upita 1");

        try {
            QueryResultDTO result = measurementService.getAverageTemperatureByLocation();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Greška pri izvršavanju upita", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Kompleksan upit 2: Merenja sa visokim temperaturama
     * GET /api/measurements/queries/high-temperature?threshold=25.5
     */
    @GetMapping("/queries/high-temperature")
    public ResponseEntity<QueryResultDTO> getHighTemperature(
            @RequestParam(defaultValue = "25.0") double threshold) {
        logger.info("GET /measurements/queries/high-temperature - Izvršavanje kompleksnog upita 2");

        try {
            QueryResultDTO result = measurementService.getHighTemperatureMeasurements(threshold);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Greška pri izvršavanju upita", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Kompleksan upit 3: Maksimalne vrednosti po satu
     * GET /api/measurements/queries/max-per-hour
     */
    @GetMapping("/queries/max-per-hour")
    public ResponseEntity<QueryResultDTO> getMaxPerHour() {
        logger.info("GET /measurements/queries/max-per-hour - Izvršavanje kompleksnog upita 3");

        try {
            QueryResultDTO result = measurementService.getMaxValuesPerHour();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Greška pri izvršavanju upita", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Pronađi ukupan broj merenja
     * GET /api/measurements/count
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getTotalCount() {
        logger.info("GET /measurements/count - Prebrojavanje ukupnog broja merenja");

        try {
            long count = measurementService.getTotalMeasurements();

            Map<String, Long> response = new HashMap<>();
            response.put("total_measurements", count);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Greška pri brojanju merenja", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check
     * GET /api/measurements/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        logger.info("GET /measurements/health - Health check");

        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "InfluxDB Measurements Microservice");

        return ResponseEntity.ok(response);
    }
}
