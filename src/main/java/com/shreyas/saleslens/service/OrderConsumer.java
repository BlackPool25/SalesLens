package com.shreyas.saleslens.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

    @KafkaListener(topics = "inventory-updates", groupId = "saleslens-group")
    public void receiveOrderEvent(String message) {
        System.out.println("Received order event: " + message);
    }
}
