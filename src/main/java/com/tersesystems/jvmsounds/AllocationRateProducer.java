package com.tersesystems.jvmsounds;

import jvm_alloc_rate_meter.MeterThread;

import java.util.function.Consumer;

public class AllocationRateProducer {
    private final MeterThread meterThread;

    public AllocationRateProducer(Consumer<Double> rateConsumer) {
        int intervalMs = 50;
        meterThread = new MeterThread(bytes -> {
            double mbytes = bytes / 1e6;
            rateConsumer.accept(mbytes);
        }, intervalMs);
    }

    public void start() {
        meterThread.start();
    }

    public void stop() {
        meterThread.terminate();
    }
}
