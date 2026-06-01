package com.influxdb.app.service;

import com.influxdb.app.dto.OpenMeteoResponse;
import com.influxdb.app.repository.InfluxDBRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

/**
 * Povlači realne vremenske podatke sa Open-Meteo API-ja (bez API ključa) i upisuje ih
 * u InfluxDB sa pravim vremenskim oznakama. Pošto je ID determinističan po (lokacija, vreme),
 * ponovno povlačenje istih sati prepisuje postojeće tačke umesto da ih duplira.
 */
@Service
public class WeatherDataService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(WeatherDataService.class);

    private static final String MEASUREMENT = "weather";

    /** Gradovi za koje povlačimo prognozu (ime, geo. širina, geo. dužina). */
    private static final List<City> CITIES = List.of(
            new City("Beograd", 44.7866, 20.4489),
            new City("Novi Sad", 45.2671, 19.8335),
            new City("Nis", 43.3209, 21.8958),
            new City("Zemun", 44.8433, 20.4011),
            new City("Niksic", 42.7731, 18.9447)
    );

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.open-meteo.com")
            .build();

    @Autowired
    private InfluxDBRepository influxDBRepository;

    @Value("${weather.enabled:true}")
    private boolean enabled;

    @Value("${weather.past-days:14}")
    private int pastDays;

    @Value("${weather.forecast-days:2}")
    private int forecastDays;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            logger.info("Povlačenje vremenskih podataka je onemogućeno (weather.enabled=false)");
            return;
        }
        fetchAllCities();
    }

    /**
     * Periodično osvežavanje (na svaki sat). Prepisuje postojeće tačke i dodaje nove sate.
     */
    @Scheduled(fixedRateString = "${weather.refresh-ms:3600000}",
               initialDelayString = "${weather.refresh-ms:3600000}")
    public void scheduledRefresh() {
        if (!enabled) {
            return;
        }
        logger.info("Zakazano osvežavanje vremenskih podataka...");
        fetchAllCities();
    }

    private void fetchAllCities() {
        long start = System.currentTimeMillis();
        int totalPoints = 0;

        for (City city : CITIES) {
            try {
                totalPoints += fetchCity(city);
            } catch (Exception e) {
                logger.error("Greška pri povlačenju podataka za {}: {}", city.name(), e.getMessage());
            }
        }

        logger.info("Vremenski podaci upisani: {} tačaka iz {} gradova za {} ms",
                totalPoints, CITIES.size(), System.currentTimeMillis() - start);
    }

    private int fetchCity(City city) {
        OpenMeteoResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/forecast")
                        .queryParam("latitude", city.latitude())
                        .queryParam("longitude", city.longitude())
                        .queryParam("hourly", "temperature_2m,relative_humidity_2m,surface_pressure")
                        .queryParam("past_days", pastDays)
                        .queryParam("forecast_days", forecastDays)
                        .queryParam("timeformat", "unixtime")
                        .queryParam("timezone", "UTC")
                        .build())
                .retrieve()
                .body(OpenMeteoResponse.class);

        if (response == null || response.getHourly() == null || response.getHourly().getTime() == null) {
            logger.warn("Prazan odgovor za grad {}", city.name());
            return 0;
        }

        OpenMeteoResponse.Hourly h = response.getHourly();
        int count = h.getTime().size();
        int written = 0;

        for (int i = 0; i < count; i++) {
            Double temperature = valueAt(h.getTemperature(), i);
            Double humidity = valueAt(h.getHumidity(), i);
            Double pressure = valueAt(h.getPressure(), i);

            if (temperature == null) {
                continue;
            }

            Instant time = Instant.ofEpochSecond(h.getTime().get(i));

            influxDBRepository.writeMeasurementAt(
                    MEASUREMENT,
                    city.name(),
                    temperature,
                    humidity != null ? humidity : 0.0,
                    pressure != null ? pressure : 0.0,
                    time
            );
            written++;
        }

        logger.info("Grad {}: upisano {} satnih merenja", city.name(), written);
        return written;
    }

    private Double valueAt(List<Double> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    private record City(String name, double latitude, double longitude) {
    }
}
