package com.influxdb.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Mapiranje odgovora Open-Meteo API-ja (https://open-meteo.com/).
 * Tražimo hourly podatke sa timeformat=unixtime, pa je vreme lista epoch sekundi (UTC).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoResponse {

    private double latitude;
    private double longitude;
    private Hourly hourly;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hourly {
        private List<Long> time;

        @JsonProperty("temperature_2m")
        private List<Double> temperature;

        @JsonProperty("relative_humidity_2m")
        private List<Double> humidity;

        @JsonProperty("surface_pressure")
        private List<Double> pressure;
    }
}
