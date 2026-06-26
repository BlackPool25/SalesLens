package com.shreyas.saleslens.batch.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class ExcelHeaderDetector {

    private ExcelHeaderDetector() {
    }

    static List<String> detect(Resource resource) {
        ZipSecureFile.setMinInflateRatio(0.0001);
        try (InputStream is = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                if (cell == null || cell.getCellType() == CellType.BLANK) {
                    headers.add(null);
                } else {
                    headers.add(formatter.formatCellValue(cell));
                }
            }
            return headers;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Excel headers from " + resource.getDescription(), e);
        }
    }
}
