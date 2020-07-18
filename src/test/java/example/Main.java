/**
 * License
 * -------
 * Written in 2020 by Will Sargent will@tersesystems.com
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>
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

        try {
            JVMSounds jvmSounds = new JVMSounds();
            try {
                jvmSounds.start();
                createGarbage(wastage);
                Thread.sleep(7500 * 10);
            } finally {
                jvmSounds.stop();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Create a very inefficient way of writing to a file.
    // I could probably just create a heap buffer and throw it away, but this is more "logging" oriented.
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
