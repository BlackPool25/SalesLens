package com.shreyas.saleslens.dto;

import com.shreyas.saleslens.model.enums.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Request body for creating a new data source")
public class CreateSourceRequest {
    @NotBlank
    @Schema(description = "Name of the data source", example = "Superstore Sales")
    private String name;

    @NotNull
    @Schema(description = "Type of data source", example = "CSV_FILE")
    private SourceType sourceType;

    @NotNull
    @Schema(description = "Trust score assigned to this source (0.0 - 1.0)", example = "0.9")
    private BigDecimal trustScore;

    @NotNull
    @Schema(description = "Whether this source is active for ingestion", example = "true")
    private Boolean active;

    @Schema(description = "JSON string with connection configuration (e.g. file path, JDBC URL)", example = "{\"filePath\": \"/tmp/file.csv\"}")
    private String connectionConfig;
}
