package com.influxdb.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResultDTO {
    private String query;
    private List<MeasurementPointDTO> results;
    private int count;
}
