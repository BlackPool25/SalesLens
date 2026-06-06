package com.shreyas.saleslens.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendOrderEvent(String message) {
        System.out.println("Sending order event: " + message);
        kafkaTemplate.send("inventory-updates", message);
    }
}
