package com.shreyas.saleslens.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class StreamKafkaConfig {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${saleslens.kafka.stream.concurrency:3}")
    private int concurrency;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaStreamListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setSyncCommits(true);
        factory.setCommonErrorHandler(streamErrorHandler());
        return factory;
    }

    @Bean
    public CommonErrorHandler streamErrorHandler() {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    meterRegistry.counter("saleslens.stream.messages.dlt",
                            "topic", "sales.live").increment();
                    return new TopicPartition("sales.live.DLT", record.partition());
                });
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(1000L, 3L));
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Kafka stream retry {}/3 for record {}: {}", deliveryAttempt, record.key(), ex.getMessage()));
        return errorHandler;
    }
}
