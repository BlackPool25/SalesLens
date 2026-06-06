package com.shreyas.saleslens.batch.csv;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

final class CsvHeaderDetector {

    private CsvHeaderDetector() {
    }

    static List<String> detect(Resource resource) {
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new IllegalStateException("CSV file is empty: " + resource.getDescription());
            }
            return Arrays.asList(header);
        } catch (IOException | CsvValidationException e) {
            throw new IllegalStateException("Failed to read CSV headers from " + resource.getDescription(), e);
        }
    }
}
