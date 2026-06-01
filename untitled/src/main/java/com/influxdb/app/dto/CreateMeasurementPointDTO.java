package com.influxdb.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeasurementPointDTO {
    private String measurement;
    private String location;
    private double temperature;
    private double humidity;
    private double pressure;
}
