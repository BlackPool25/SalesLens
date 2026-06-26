package com.shreyas.saleslens.service.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnknownSourceExceptionTest {

    @Test
    void constructor_setsMessageAndSourceSystem() {
        UnknownSourceException exception = new UnknownSourceException("test_system");

        assertThat(exception.getMessage()).contains("test_system");
        assertThat(exception.getSourceSystem()).isEqualTo("test_system");
    }
}
