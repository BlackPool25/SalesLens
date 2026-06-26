package com.shreyas.saleslens.batch.excel;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Component
@StepScope
public class ExcelItemReader implements ItemReader<Map<String, String>>, ItemStream {

    private static final Logger log = LoggerFactory.getLogger(ExcelItemReader.class);

    private final String filePath;
    private Workbook workbook;
    private Sheet sheet;
    private Iterator<Row> rowIterator;
    private List<String> headers;
    private int currentRowNum;

    public ExcelItemReader(
            @Value("#{jobParameters['filePath']}") String filePath) {
        this.filePath = filePath;
    }

    @Override
    public void open(ExecutionContext ctx) {
        ZipSecureFile.setMinInflateRatio(0.0001);
        try {
            this.workbook = WorkbookFactory.create(new File(filePath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open Excel file: " + filePath, e);
        }
        this.sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            this.headers = List.of();
            this.rowIterator = Collections.emptyIterator();
            return;
        }
        DataFormatter formatter = new DataFormatter();
        this.headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                headers.add(null);
            } else {
                headers.add(formatter.formatCellValue(cell));
            }
        }
        this.rowIterator = sheet.iterator();
        // Skip header row
        if (rowIterator.hasNext()) {
            rowIterator.next();
        }
        this.currentRowNum = 1;
        log.debug("Opened Excel file '{}' with {} columns and {} data rows",
                filePath, headers.size(), sheet.getLastRowNum());
    }

    @Override
    public Map<String, String> read() {
        if (rowIterator == null || !rowIterator.hasNext()) {
            return null;
        }
        Row row = rowIterator.next();
        DataFormatter formatter = new DataFormatter();
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i);
            String value = null;
            if (cell != null) {
                switch (cell.getCellType()) {
                    case BLANK -> value = null;
                    case STRING -> value = cell.getStringCellValue();
                    case NUMERIC -> value = formatter.formatCellValue(cell);
                    case BOOLEAN -> value = String.valueOf(cell.getBooleanCellValue());
                    case FORMULA -> value = formatter.formatCellValue(cell, evaluator);
                    case ERROR -> value = null;
                    default -> value = null;
                }
            }
            result.put(headers.get(i), value);
        }
        currentRowNum++;
        return result;
    }

    @Override
    public void close() {
        if (workbook != null) {
            try {
                workbook.close();
            } catch (IOException e) {
                log.warn("Failed to close workbook for '{}'", filePath, e);
            }
        }
    }

    @Override
    public void update(ExecutionContext ctx) {
        // no-op
    }
}
