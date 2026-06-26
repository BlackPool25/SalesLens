package com.shreyas.saleslens.repository;

import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.enums.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSourceRepositoryTest {

    @Mock
    private DataSourceRepository repository;

    @Test
    void findBySourceTypeAndActive_returnsMatchingSources() {
        DataSource source = new DataSource();
        source.setSourceType(SourceType.KAFKA_STREAM);
        source.setActive(true);

        when(repository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(source));

        List<DataSource> result = repository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true);

        assertEquals(1, result.size());
        assertEquals(SourceType.KAFKA_STREAM, result.getFirst().getSourceType());
        assertTrue(result.getFirst().getActive());
    }

    @Test
    void findBySourceTypeAndActive_noMatch_returnsEmpty() {
        when(repository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of());

        List<DataSource> result = repository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true);

        assertTrue(result.isEmpty());
    }
}
