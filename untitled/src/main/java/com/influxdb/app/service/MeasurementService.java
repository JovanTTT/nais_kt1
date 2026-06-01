package com.influxdb.app.service;

import com.influxdb.app.dto.CreateMeasurementPointDTO;
import com.influxdb.app.dto.MeasurementPointDTO;
import com.influxdb.app.dto.QueryResultDTO;
import com.influxdb.app.repository.InfluxDBRepository;
import com.influxdb.app.repository.RedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class MeasurementService {

    private static final Logger logger = LoggerFactory.getLogger(MeasurementService.class);

    @Autowired
    private InfluxDBRepository influxDBRepository;

    @Autowired
    private RedisRepository redisRepository;

    /**
     * Kreiraj novo merenje
     */
    public MeasurementPointDTO createMeasurement(CreateMeasurementPointDTO dto) {
        logger.info("Kreiranje merenja za lokaciju: {}", dto.getLocation());

        MeasurementPointDTO created = influxDBRepository.createMeasurementPoint(
                dto.getMeasurement(),
                dto.getLocation(),
                dto.getTemperature(),
                dto.getHumidity(),
                dto.getPressure()
        );

        // Keširaj rezultat
        redisRepository.cacheMeasurement(created);
        redisRepository.incrementCounter("created_measurements");

        logger.info("Merenje uspešno kreirano sa ID: {}", created.getId());
        return created;
    }

    /**
     * Obriši merenje po ID-u
     */
    public boolean deleteMeasurement(String id) {
        logger.info("Brisanje merenja sa ID: {}", id);

        boolean deleted = influxDBRepository.deleteMeasurementPoint(id);
        if (deleted) {
            redisRepository.removeCachedMeasurement(id);
            redisRepository.incrementCounter("deleted_measurements");
        }

        return deleted;
    }

    /**
     * Pronađi merenja po lokaciji
     */
    public QueryResultDTO findByLocation(String location) {
        logger.info("Pretraga merenja za lokaciju: {}", location);

        List<MeasurementPointDTO> results = influxDBRepository.findByLocation(location);

        return QueryResultDTO.builder()
                .query("findByLocation:" + location)
                .results(results)
                .count(results.size())
                .build();
    }

    /**
     * Pronađi merenja po tipu merenja
     */
    public QueryResultDTO findByMeasurement(String measurement) {
        logger.info("Pretraga merenja po tipu: {}", measurement);

        List<MeasurementPointDTO> results = influxDBRepository.findByMeasurement(measurement);

        return QueryResultDTO.builder()
                .query("findByMeasurement:" + measurement)
                .results(results)
                .count(results.size())
                .build();
    }

    /**
     * Pronađi merenja u vremenskom rasponu
     */
    public QueryResultDTO findByTimeRange(Instant from, Instant to) {
        logger.info("Pretraga merenja od {} do {}", from, to);

        List<MeasurementPointDTO> results = influxDBRepository.findByTimeRange(from, to);

        return QueryResultDTO.builder()
                .query("findByTimeRange")
                .results(results)
                .count(results.size())
                .build();
    }

    /**
     * Kompleksan upit 1: Prosečne temperature po lokaciji
     */
    public QueryResultDTO getAverageTemperatureByLocation() {
        logger.info("Izvršavanje kompleksnog upita 1: Prosečne temperature");

        List<MeasurementPointDTO> results = influxDBRepository.getAverageTemperatureByLocation();

        return QueryResultDTO.builder()
                .query("getAverageTemperatureByLocation")
                .results(results)
                .count(results.size())
                .build();
    }

    /**
     * Kompleksan upit 2: Visoke temperature
     */
    public QueryResultDTO getHighTemperatureMeasurements(double threshold) {
        logger.info("Izvršavanje kompleksnog upita 2: Merenja sa visokim temperaturama (> {})", threshold);

        List<MeasurementPointDTO> results = influxDBRepository.getHighTemperatureMeasurements(threshold);

        return QueryResultDTO.builder()
                .query("getHighTemperatureMeasurements")
                .threshold(threshold)
                .results(results)
                .count(results.size())
                .build();
    }

    /**
     * Kompleksan upit 3: Maksimalne vrednosti po satu
     */
    public QueryResultDTO getMaxValuesPerHour() {
        logger.info("Izvršavanje kompleksnog upita 3: Maksimalne vrednosti po satu");

        List<MeasurementPointDTO> results = influxDBRepository.getMaxValuesPerHour();

        return QueryResultDTO.builder()
                .query("getMaxValuesPerHour")
                .results(results)
                .count(results.size())
                .build();
    }

    /**
     * Broj ukupnih merenja
     */
    public long getTotalMeasurements() {
        logger.info("Preuzimanje broja ukupnih merenja");
        return influxDBRepository.countMeasurements();
    }

    /**
     * Pronađi merenje po ID-u iz keša
     */
    public MeasurementPointDTO getMeasurementFromCache(String id) {
        logger.info("Preuzimanje merenja iz keša sa ID: {}", id);
        return redisRepository.getCachedMeasurement(id);
    }
}
