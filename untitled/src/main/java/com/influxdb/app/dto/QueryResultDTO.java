package com.influxdb.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryResultDTO {
    private String query;
    private List<MeasurementPointDTO> results;
    private int count;
    /** Prag temperature (°C) za upit visokih temperatura. */
    private Double threshold;
}
