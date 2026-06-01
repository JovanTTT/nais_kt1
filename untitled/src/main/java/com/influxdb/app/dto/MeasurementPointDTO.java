package com.influxdb.app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeasurementPointDTO {
    private String id;
    private String measurement;
    private String location;
    private Double temperature;
    private Double humidity;
    private Double pressure;
    /** Broj očitavanja temperature iznad praga (kompleksan upit 2). */
    private Long readingsAboveThreshold;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant timestamp;
}
