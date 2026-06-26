package com.shreyas.saleslens.util;

import com.shreyas.saleslens.service.OrderProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @deprecated Test-only utility. Phase 8 uses LiveSalesEventConsumer for stream ingestion.
 */
@Deprecated
@Component
@RequiredArgsConstructor
public class ProducerTrigger {

    private final OrderProducer orderProducer;

//    @Scheduled(fixedRate = 5000)
    public void scheduleMessages() {
        int randomInt = ThreadLocalRandom.current().nextInt(0, 101);

        orderProducer.sendOrderEvent(String.valueOf(randomInt));
    }
}
