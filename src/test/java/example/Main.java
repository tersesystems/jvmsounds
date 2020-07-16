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
package example;

import com.tersesystems.jvmsounds.JVMSounds;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        // Increase this number to waste more memory
        int wastage = 10000;

        JVMSounds jvmSounds = new JVMSounds();
        try {
            jvmSounds.start();
            createGarbage(wastage);
            Thread.sleep(7500 * 10);
        } finally {
            jvmSounds.stop();
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

}
