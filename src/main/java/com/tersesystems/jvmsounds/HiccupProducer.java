package com.tersesystems.jvmsounds;

import  jvm_hiccup_meter.MeterThread;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * https://github.com/clojure-goes-fast/jvm-hiccup-meter
 */
public class HiccupProducer {

    private final MeterThread meterThread;

    public HiccupProducer(Consumer<Long> callback) {
        int resolutionMs = 50;
        meterThread = new MeterThread(callback::accept, resolutionMs);
    }

    public void start() {
        meterThread.start();
    }

    public void stop() {
        meterThread.terminate();
    }
}
