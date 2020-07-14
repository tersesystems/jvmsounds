/*
 * SPDX-License-Identifier: CC0-1.0
 *
 * Copyright 2020 Will Sargent.
 *
 * Licensed under the CC0 Public Domain Dedication;
 * You may obtain a copy of the License at
 *
 *  http://creativecommons.org/publicdomain/zero/1.0/
 */
package com.tersesystems.alloctone;

import jvm_alloc_rate_meter.MeterThread;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;
import java.util.function.LongConsumer;

/**
 * A class which plays the memory allocation tone rate as a sine tone through
 * the Java Audio API.
 * <p>
 * Please see the <a href="https://tersesystems.com/blog/2020/07/09/logging-vs-memory/">Logging vs Memory</a> for more details.
 * <p>
 * The allocation rate is taken from <a href="https://github.com/clojure-goes-fast/jvm-alloc-rate-meter">jvm-alloc-rate-meter</a> by
 * alexander-yakushev.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        AtomicLong allocRate = new AtomicLong(0L);
        DoubleSupplier supplier = () -> {
            double v = allocRate.longValue() / 1e6;
            System.out.println("Allocation rate in MiB/sec = " + v);
            return v;
        };
        SoftSynth synth = new SoftSynth(supplier);

        try {
            MeterThread meterThread = new MeterThread(allocRate::set);
            meterThread.start();
            createGarbage();
            Thread.sleep(7500 * 1000);
        } finally {
            synth.stop();
        }
    }

    // Create a very inefficient way of writing to a file.
    private static void createGarbage() {
        Runnable garbageProducingRunnable = () -> {
            try {
                Path tempFile = Files.createTempFile("tmp", "tmp");
                while (true) {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()),  100000)) {
                        long l = System.currentTimeMillis();
                        StringBuffer b = new StringBuffer().append(Math.random()).append("derp").append(l);
                        writer.write(b.toString());
                        if (Files.size(tempFile) > 1024 * 1000) {
                            Files.delete(tempFile);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        Thread t1 = new Thread(garbageProducingRunnable);
        t1.setDaemon(true);
        t1.start();
    }
}
