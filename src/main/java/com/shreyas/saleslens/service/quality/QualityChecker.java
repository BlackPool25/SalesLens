package com.shreyas.saleslens.service.quality;

import com.shreyas.saleslens.model.QualityIssue;
import com.shreyas.saleslens.model.QualityRun;
import com.shreyas.saleslens.model.StagedRecord;
import com.shreyas.saleslens.model.enums.QualityDimension;

import java.util.List;

/**
 * Contract for a single data-quality dimension checker.
 * Each implementation inspects a {@link StagedRecord}'s {@code rawPayload} JSON
 * and returns zero or more {@link QualityIssue} objects.
 */
public interface QualityChecker {

    /** The dimension this checker is responsible for. */
    QualityDimension dimension();

    /**
     * Validate the record and return a (possibly empty) list of issues.
     * The returned issues must NOT be persisted yet — that is done by
     * {@link QualityEngineService}.
     */
    List<QualityIssue> check(StagedRecord record, QualityRun run);
}
