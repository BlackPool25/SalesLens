package com.shreyas.saleslens.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SchemaResponse {
    private UUID id;
    private int version;
    private String status;
    private List<FieldResponse> fields;
    private String inferredAt;

    @Getter
    @Setter
    public static class FieldResponse {
        private String fieldName;
        private String inferredType;
        private String detectedFormat;
        private boolean nullable;
        private List<String> sampleValues;
    }
}
