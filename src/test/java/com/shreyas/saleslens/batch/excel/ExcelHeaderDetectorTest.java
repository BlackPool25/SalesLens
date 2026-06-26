package com.shreyas.saleslens.batch.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelHeaderDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detect_normalHeaders_returnsList() throws IOException {
        Path file = tempDir.resolve("headers.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Name");
            row.createCell(1).setCellValue("Email");
            row.createCell(2).setCellValue("Phone");
            try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                workbook.write(out);
            }
        }

        List<String> headers = ExcelHeaderDetector.detect(new FileSystemResource(file.toFile()));

        assertThat(headers).containsExactly("Name", "Email", "Phone");
    }

    @Test
    void detect_singleHeader_returnsSingleElement() throws IOException {
        Path file = tempDir.resolve("single.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("OnlyHeader");
            try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                workbook.write(out);
            }
        }

        List<String> headers = ExcelHeaderDetector.detect(new FileSystemResource(file.toFile()));

        assertThat(headers).containsExactly("OnlyHeader");
    }

    @Test
    void detect_emptyFirstRow_returnsEmptyList() throws IOException {
        Path file = tempDir.resolve("empty.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sheet1");
            // No rows created — sheet.getRow(0) returns null
            try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                workbook.write(out);
            }
        }

        List<String> headers = ExcelHeaderDetector.detect(new FileSystemResource(file.toFile()));

        assertThat(headers).isEmpty();
    }

    @Test
    void detect_numericHeader_returnsString() throws IOException {
        Path file = tempDir.resolve("numeric.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue(2024);
            try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                workbook.write(out);
            }
        }

        List<String> headers = ExcelHeaderDetector.detect(new FileSystemResource(file.toFile()));

        assertThat(headers).containsExactly("2024");
    }

    @Test
    void detect_blankCell_returnsNull() throws IOException {
        Path file = tempDir.resolve("blank.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Present");
            Cell blankCell = row.createCell(1);
            blankCell.setBlank();
            row.createCell(2).setCellValue("AlsoPresent");
            try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                workbook.write(out);
            }
        }

        List<String> headers = ExcelHeaderDetector.detect(new FileSystemResource(file.toFile()));

        assertThat(headers).hasSize(3);
        assertThat(headers.get(0)).isEqualTo("Present");
        assertThat(headers.get(1)).isNull();
        assertThat(headers.get(2)).isEqualTo("AlsoPresent");
    }

    @Test
    void detect_corruptedFile_throwsException() throws IOException {
        Path file = tempDir.resolve("corrupted.xlsx");
        java.nio.file.Files.writeString(file, "this is not an excel file");

        assertThatThrownBy(() -> ExcelHeaderDetector.detect(new FileSystemResource(file.toFile())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read Excel headers");
    }
}
