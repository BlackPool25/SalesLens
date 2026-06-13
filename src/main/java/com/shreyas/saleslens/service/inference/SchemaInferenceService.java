package com.shreyas.saleslens.service.inference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.*;
import com.shreyas.saleslens.model.enums.InferredType;
import com.shreyas.saleslens.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import com.shreyas.saleslens.service.SemanticMapperService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchemaInferenceService {

    private final StagedRecordRepository stagedRecordRepository;
    private final SourceSchemaRepository sourceSchemaRepository;
    private final SourceSchemaFieldRepository sourceSchemaFieldRepository;
    private final DataProfileRepository dataProfileRepository;
    private final FieldProfileRepository fieldProfileRepository;
    private final TypeDetectionService typeDetectionService;
    private final IngestionJobRepository ingestionJobRepository;
    private final SemanticMapperService semanticMapperService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void runInference(UUID jobId) {
        IngestionJob job = ingestionJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        DataSource source = job.getSource();

        List<StagedRecord> records = stagedRecordRepository.findByJobId(jobId, PageRequest.of(0, 500));
        if (records.isEmpty()) {
            return;
        }

        List<Map<String, String>> parsedPayloads = new ArrayList<>();
        for (StagedRecord record : records) {
            parsedPayloads.add(parsePayload(record.getRawPayload()));
        }

        Set<String> fieldNames = new LinkedHashSet<>();
        for (Map<String, String> payload : parsedPayloads) {
            fieldNames.addAll(payload.keySet());
        }

        int totalRecords = records.size();
        List<InferredFieldInfo> inferredFields = new ArrayList<>();

        for (String fieldName : fieldNames) {
            List<String> rawValues = parsedPayloads.stream()
                    .map(p -> p.get(fieldName))
                    .toList();

            long nullCount = rawValues.stream()
                    .filter(v -> v == null || v.trim().isEmpty())
                    .count();

            BigDecimal nullRate = BigDecimal.valueOf((double) nullCount / totalRecords)
                    .setScale(4, RoundingMode.HALF_UP);

            List<String> nonNullNonEmpty = rawValues.stream()
                    .filter(v -> v != null && !v.trim().isEmpty())
                    .map(String::trim)
                    .toList();

            InferredType inferredType = typeDetectionService.detectType(nonNullNonEmpty);
            String detectedFormat = typeDetectionService.detectDateFormat(nonNullNonEmpty);
            boolean nullable = nullCount > 0;

            int uniqueCount = (int) nonNullNonEmpty.stream().distinct().count();

            Map<String, Long> frequencyMap = nonNullNonEmpty.stream()
                    .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

            List<String> top10 = frequencyMap.entrySet().stream()
                    .sorted((e1, e2) -> {
                        int compare = e2.getValue().compareTo(e1.getValue());
                        if (compare == 0) {
                            return e1.getKey().compareTo(e2.getKey());
                        }
                        return compare;
                    })
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .toList();

            String topValuesJson = serializeJson(top10);

            String minValue = null;
            String maxValue = null;
            if (!nonNullNonEmpty.isEmpty()) {
                if (inferredType == InferredType.INTEGER || inferredType == InferredType.DECIMAL) {
                    BigDecimal minDec = null;
                    BigDecimal maxDec = null;
                    for (String val : nonNullNonEmpty) {
                        try {
                            BigDecimal dec = new BigDecimal(val);
                            if (minDec == null || dec.compareTo(minDec) < 0) {
                                minDec = dec;
                            }
                            if (maxDec == null || dec.compareTo(maxDec) > 0) {
                                maxDec = dec;
                            }
                        } catch (NumberFormatException e) {
                        }
                    }
                    if (minDec != null) {
                        minValue = minDec.toString();
                    }
                    if (maxDec != null) {
                        maxValue = maxDec.toString();
                    }
                } else {
                    minValue = nonNullNonEmpty.stream().min(String::compareTo).orElse(null);
                    maxValue = nonNullNonEmpty.stream().max(String::compareTo).orElse(null);
                }
            }

            List<String> samples = nonNullNonEmpty.stream().distinct().limit(5).toList();
            String sampleValuesJson = serializeJson(samples);

            InferredFieldInfo info = new InferredFieldInfo();
            info.fieldName = fieldName;
            info.inferredType = inferredType;
            info.detectedFormat = detectedFormat;
            info.nullable = nullable;
            info.sampleValuesJson = sampleValuesJson;
            info.nullRate = nullRate;
            info.uniqueCount = uniqueCount;
            info.topValuesJson = topValuesJson;
            info.minValue = minValue;
            info.maxValue = maxValue;

            inferredFields.add(info);
        }

        Optional<SourceSchema> prevActiveOpt = sourceSchemaRepository.findBySourceIdAndStatus(source.getId(), SourceSchema.STATUS_ACTIVE);
        SourceSchema targetSchema;

        if (prevActiveOpt.isPresent()) {
            SourceSchema prevActive = prevActiveOpt.get();
            if (hasDrifted(inferredFields, prevActive)) {
                prevActive.setStatus(SourceSchema.STATUS_SUPERSEDED);
                sourceSchemaRepository.save(prevActive);

                Optional<SourceSchema> latestSchemaOpt = sourceSchemaRepository.findTopBySourceIdOrderByVersionDesc(source.getId());
                int nextVersion = latestSchemaOpt.map(s -> s.getVersion() + 1).orElse(1);

                SourceSchema newSchema = new SourceSchema();
                newSchema.setSource(source);
                newSchema.setVersion(nextVersion);
                newSchema.setStatus(SourceSchema.STATUS_ACTIVE);
                targetSchema = sourceSchemaRepository.save(newSchema);

                saveSchemaFields(inferredFields, targetSchema);
                semanticMapperService.generateMappings(source.getId(), targetSchema);
            } else {
                targetSchema = prevActive;
            }
        } else {
            Optional<SourceSchema> latestSchemaOpt = sourceSchemaRepository.findTopBySourceIdOrderByVersionDesc(source.getId());
            int nextVersion = latestSchemaOpt.map(s -> s.getVersion() + 1).orElse(1);

            SourceSchema newSchema = new SourceSchema();
            newSchema.setSource(source);
            newSchema.setVersion(nextVersion);
            newSchema.setStatus(SourceSchema.STATUS_ACTIVE);
            targetSchema = sourceSchemaRepository.save(newSchema);

            saveSchemaFields(inferredFields, targetSchema);
            semanticMapperService.generateMappings(source.getId(), targetSchema);
        }

        DataProfile profile = new DataProfile();
        profile.setSource(source);
        profile.setSchema(targetSchema);
        profile.setJob(job);
        profile.setTotalRecords(totalRecords);
        DataProfile savedProfile = dataProfileRepository.save(profile);

        for (InferredFieldInfo info : inferredFields) {
            FieldProfile fieldProfile = new FieldProfile();
            fieldProfile.setProfile(savedProfile);
            fieldProfile.setFieldName(info.fieldName);
            fieldProfile.setNullRate(info.nullRate);
            fieldProfile.setUniqueCount(info.uniqueCount);
            fieldProfile.setTopValues(info.topValuesJson);
            fieldProfile.setMinValue(info.minValue);
            fieldProfile.setMaxValue(info.maxValue);
            fieldProfile.setSampleValues(info.sampleValuesJson);
            fieldProfileRepository.save(fieldProfile);
        }
    }

    private boolean hasDrifted(List<InferredFieldInfo> currentFields, SourceSchema previousSchema) {
        List<SourceSchemaField> previousFields = sourceSchemaFieldRepository.findBySchemaId(previousSchema.getId());
        
        Map<String, InferredType> prevFieldTypes = previousFields.stream()
                .collect(Collectors.toMap(SourceSchemaField::getFieldName, SourceSchemaField::getInferredType));

        Map<String, InferredType> curFieldTypes = currentFields.stream()
                .collect(Collectors.toMap(info -> info.fieldName, info -> info.inferredType));

        if (prevFieldTypes.size() != curFieldTypes.size()) {
            return true;
        }

        if (!prevFieldTypes.keySet().equals(curFieldTypes.keySet())) {
            return true;
        }

        for (String fieldName : curFieldTypes.keySet()) {
            if (prevFieldTypes.get(fieldName) != curFieldTypes.get(fieldName)) {
                return true;
            }
        }

        return false;
    }

    private void saveSchemaFields(List<InferredFieldInfo> fields, SourceSchema schema) {
        for (InferredFieldInfo info : fields) {
            SourceSchemaField field = new SourceSchemaField();
            field.setSchema(schema);
            field.setFieldName(info.fieldName);
            field.setInferredType(info.inferredType);
            field.setDetectedFormat(info.detectedFormat);
            field.setNullable(info.nullable);
            field.setSampleValues(info.sampleValuesJson);
            sourceSchemaFieldRepository.save(field);
        }
    }

    private Map<String, String> parsePayload(String json) {
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, String> stringMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                stringMap.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
            }
            return stringMap;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse raw_payload: " + json, e);
        }
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON: " + value, e);
        }
    }

    private static class InferredFieldInfo {
        private String fieldName;
        private InferredType inferredType;
        private String detectedFormat;
        private boolean nullable;
        private String sampleValuesJson;
        private BigDecimal nullRate;
        private int uniqueCount;
        private String topValuesJson;
        private String minValue;
        private String maxValue;
    }
}
