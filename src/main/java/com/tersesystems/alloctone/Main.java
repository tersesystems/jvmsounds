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

import com.sun.management.GarbageCollectionNotificationInfo;
import jvm_alloc_rate_meter.MeterThread;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;

/**
 * A class which plays the memory allocation tone rate as a sine tone through
 * <a href="https://github.com/philburk/jsyn">jSyn</a>.
 * <p>
 * Please see the <a href="https://tersesystems.com/blog/2020/07/09/logging-vs-memory/">Logging vs Memory</a> for more details.
 * <p>
 * The allocation rate is taken from <a href="https://github.com/clojure-goes-fast/jvm-alloc-rate-meter">jvm-alloc-rate-meter</a> by
 * alexander-yakushev.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        // Increase this number to waste more memory
        int wastage = 1000;

        AllocationTone tone = new AllocationTone();
        try {
            AtomicLong allocRate = new AtomicLong(0L);
            DoubleSupplier supplier = () -> {
                double v = allocRate.longValue() / 1e6;
                System.out.println("Allocation rate in MiB/sec = " + v);
                return v;
            };
            tone.start(supplier, Duration.ofMillis(50));
            tone.setVolume(0.1);

            MeterThread meterThread = new MeterThread(allocRate::set);
            meterThread.start();

            List<GarbageCollectorMXBean> gcbeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gcbean : gcbeans) {
                NotificationEmitter emitter = (NotificationEmitter) gcbean;
                NotificationListener listener = new GarbageCollectionListener();
                emitter.addNotificationListener(listener, null, null);
            }

            createGarbage(wastage);
            Thread.sleep(7500 * 1000);
        } finally {
            tone.stop();
        }
    }

    // Create a very inefficient way of writing to a file.
    private static void createGarbage(int wastage) {
        Runnable garbageProducingRunnable = () -> {
            try {
                Path tempFile = Files.createTempFile("tmp", "tmp");
                while (true) {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()), wastage)) {
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

    /**
     * Print out some GC statistics.  Maybe we can make record scratches and notes on GC events.
     */
    private static class GarbageCollectionListener implements NotificationListener {
        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                //get the information associated with this notification
                GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                //get all the info and pretty print it
                long duration = info.getGcInfo().getDuration();
                String gctype = info.getGcAction();
                if ("end of minor GC".equals(gctype)) {
                    gctype = "Young Gen GC";
                } else if ("end of major GC".equals(gctype)) {
                    gctype = "Old Gen GC";
                }
                System.out.println();
                System.out.println(gctype + ": - " + info.getGcInfo().getId() + " " + info.getGcName() + " (from " + info.getGcCause() + ") " + duration + " milliseconds; start-end times " + info.getGcInfo().getStartTime() + "-" + info.getGcInfo().getEndTime());

                //Get the information about each memory space, and pretty print it
                Map<String, MemoryUsage> membefore = info.getGcInfo().getMemoryUsageBeforeGc();
                Map<String, MemoryUsage> mem = info.getGcInfo().getMemoryUsageAfterGc();
                for (Map.Entry<String, MemoryUsage> entry : mem.entrySet()) {
                    String name = entry.getKey();
                    MemoryUsage memdetail = entry.getValue();
                    long memInit = memdetail.getInit();
                    long memCommitted = memdetail.getCommitted();
                    long memMax = memdetail.getMax();
                    long memUsed = memdetail.getUsed();
                    MemoryUsage before = membefore.get(name);
                    long beforepercent = ((before.getUsed() * 1000L) / before.getCommitted());
                    long percent = ((memUsed * 1000L) / before.getCommitted()); //>100% when it gets expanded

                    System.out.print(name + (memCommitted == memMax ? "(fully expanded)" : "(still expandable)") + "used: " + (beforepercent / 10) + "." + (beforepercent % 10) + "%->" + (percent / 10) + "." + (percent % 10) + "%(" + ((memUsed / 1048576) + 1) + "MB) / ");
                }
                System.out.println();
                long totalGcDuration = info.getGcInfo().getDuration();
                long percent = totalGcDuration * 1000L / info.getGcInfo().getEndTime();
                System.out.println("GC cumulated overhead " + (percent / 10) + "." + (percent % 10) + "%");
            }
        }
    }
}
