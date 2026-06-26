package com.shreyas.saleslens.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreyas.saleslens.model.DataSource;
import com.shreyas.saleslens.model.enums.SourceType;
import com.shreyas.saleslens.repository.DataSourceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaSourceRegistryServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Counter counter;

    private ObjectMapper objectMapper;
    private KafkaSourceRegistryService registryService;

    private DataSource kafkaSource;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        sourceId = UUID.randomUUID();
        kafkaSource = new DataSource();
        kafkaSource.setId(sourceId);
        kafkaSource.setSourceType(SourceType.KAFKA_STREAM);
        kafkaSource.setActive(true);
        kafkaSource.setConnectionConfig("{\"sourceSystems\": [\"pos_01\", \"pos_02\"]}");

        registryService = new KafkaSourceRegistryService(dataSourceRepository, meterRegistry, objectMapper);
    }

    @Test
    void resolveSource_cachedHit_returnsDataSource() {
        when(dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(kafkaSource));

        registryService.refreshCache();

        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(kafkaSource));

        DataSource result = registryService.resolveSource("pos_01");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(sourceId);
        verify(dataSourceRepository).findById(sourceId);
        verify(dataSourceRepository, never()).findBySourceSystem(anyString());
    }

    @Test
    void resolveSource_cacheMiss_queriesDb() {
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(dataSourceRepository.findBySourceSystem("unknown_system"))
                .thenReturn(List.of(kafkaSource));

        DataSource result = registryService.resolveSource("unknown_system");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(sourceId);
        verify(dataSourceRepository).findBySourceSystem("unknown_system");
        verify(dataSourceRepository, never()).findById(any());
    }

    @Test
    void resolveSource_notFound_throwsUnknownSourceException() {
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(dataSourceRepository.findBySourceSystem("nonexistent"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> registryService.resolveSource("nonexistent"))
                .isInstanceOf(UnknownSourceException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void refreshCache_prepopulatesCache() {
        when(dataSourceRepository.findBySourceTypeAndActive(SourceType.KAFKA_STREAM, true))
                .thenReturn(List.of(kafkaSource));

        registryService.refreshCache();

        when(dataSourceRepository.findById(sourceId)).thenReturn(Optional.of(kafkaSource));

        DataSource cachedPos1 = registryService.resolveSource("pos_01");
        DataSource cachedPos2 = registryService.resolveSource("pos_02");

        assertThat(cachedPos1.getId()).isEqualTo(sourceId);
        assertThat(cachedPos2.getId()).isEqualTo(sourceId);
        verify(dataSourceRepository, times(2)).findById(sourceId);
        verify(dataSourceRepository, never()).findBySourceSystem(anyString());
    }
}
