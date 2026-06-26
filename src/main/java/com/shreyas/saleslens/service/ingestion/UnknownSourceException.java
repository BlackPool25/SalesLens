package com.shreyas.saleslens.service.ingestion;

public class UnknownSourceException extends RuntimeException {
    private final String sourceSystem;

    public UnknownSourceException(String sourceSystem) {
        super("Unknown Kafka source system: " + sourceSystem);
        this.sourceSystem = sourceSystem;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }
}
