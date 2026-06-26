package com.shreyas.saleslens.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StreamKafkaConfigTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private ConsumerFactory<String, String> consumerFactory;

    private StreamKafkaConfig config;

    @BeforeEach
    void setUp() {
        config = new StreamKafkaConfig(kafkaTemplate, meterRegistry);
        ReflectionTestUtils.setField(config, "concurrency", 3);
    }

    @Test
    void kafkaStreamListenerContainerFactory_hasRecordAckMode() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaStreamListenerContainerFactory(consumerFactory);

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    @Test
    void kafkaStreamListenerContainerFactory_hasSyncCommits() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaStreamListenerContainerFactory(consumerFactory);

        assertThat(factory.getContainerProperties().isSyncCommits()).isTrue();
    }

    @Test
    void streamErrorHandler_returnsDefaultErrorHandler() {
        CommonErrorHandler errorHandler = config.streamErrorHandler();

        assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
    }
}
