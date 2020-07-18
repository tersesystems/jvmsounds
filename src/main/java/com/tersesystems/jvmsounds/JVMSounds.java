/**
 * License
 * -------
 * Written in 2020 by Will Sargent will@tersesystems.com
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>
 */
package com.tersesystems.jvmsounds;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.data.AudioSample;
import com.jsyn.instruments.DrumWoodFM;
import com.jsyn.instruments.NoiseHit;
import com.jsyn.unitgen.*;
import com.jsyn.util.SampleLoader;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import jvm_alloc_rate_meter.MeterThread;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION;
import static com.sun.management.GarbageCollectionNotificationInfo.from;

/**
 * A class which plays the memory allocation tone rate as a sine tone through
 * <a href="https://github.com/philburk/jsyn">jSyn</a>.
 * <p>
 * Please see the <a href="https://tersesystems.com/blog/2020/07/09/logging-vs-memory/">Logging vs Memory</a> for more details.
 * <p>
 * The allocation rate is taken from <a href="https://github.com/clojure-goes-fast/jvm-alloc-rate-meter">jvm-alloc-rate-meter</a> by
 * alexander-yakushev.
 */
public class JVMSounds {

    /** Default allocation threshold in megabytes per second. */
    public static final int DEFAULT_ALLOC_THRESHOLD = 300;

    public static final double DEFAULT_ALLOC_VOLUME = 0.1;

    public static final double DEFAULT_MINOR_GC_VOLUME = 0.6;

    public static final double DEFAULT_MIXED_GC_VOLUME = 0.6;

    /** Default hiccup threshold in milliseconds. */
    public static final int DEFAULT_HICCUP_THRESHOLD = 50;
    public static final double DEFAULT_HICCUP_VOLUME = 1.0;
    public static final int DEFAULT_NAP_TIME = 0;

    private final Logger logger = Logger.getLogger(JVMSounds.class.getName());

    private final Synthesizer synth;
    private final LineOut lineOut;

    private final SineOscillator allocTone;
    private final DrumWoodFM minorGcSound;
    private final NoiseHit mixedGcSound;
    private final AudioSample hiccupSound;
    private final VariableRateDataReader hiccupPlayer;

    private final HiccupProducer hiccupProducer;
    private final AllocationRateProducer allocProducer;
    private final GCEventProducer gcEventProducer;

    private final Configuration config = new Configuration();

    public static void premain(String argsString, Instrumentation inst) throws IOException {
        String[] args = (argsString != null) ? argsString.split("[ ,;]+") : new String[0];
        commonMain(args);
    }

    private static void nap(long millis) {
        try {
            long startMillis = System.currentTimeMillis();
            while (System.currentTimeMillis() - startMillis < millis) {
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static JVMSounds commonMain(String[] args) throws IOException {
        JVMSounds jvmSounds = null;
        try {
            jvmSounds = new JVMSounds(args);
            jvmSounds.start();
        } catch (FileNotFoundException e) {
            System.err.println("heaputils: Failed to open log file.");
        }
        return jvmSounds;
    }

    public JVMSounds() throws IOException {
        this(new String[0]);
    }

    public JVMSounds(String[] args) throws IOException {
        config.parseArgs(args);

        synth = JSyn.createSynthesizer();
        allocTone = new SineOscillator();
        lineOut = new LineOut();
        minorGcSound = new DrumWoodFM();
        mixedGcSound = new NoiseHit();

        synth.add(allocTone);
        synth.add(lineOut);
        synth.add(minorGcSound);
        synth.add(mixedGcSound);

        allocTone.output.connect(0, lineOut.input, 0);
        allocTone.output.connect(0, lineOut.input, 1);

        minorGcSound.getOutput().connect(0, lineOut.input, 0);
        minorGcSound.getOutput().connect(0, lineOut.input, 1);

        mixedGcSound.getOutput().connect(0, lineOut.input, 0);
        mixedGcSound.getOutput().connect(0, lineOut.input, 1);

        allocProducer = new AllocationRateProducer(this::setAllocTone);
        gcEventProducer = new GCEventProducer(this::playMinorGC, this::playMixedGC);
        hiccupProducer = new HiccupProducer(this::hiccup);
        SampleLoader.setJavaSoundPreferred(false);
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream("hiccup.wav");
        hiccupSound = SampleLoader.loadFloatSample(resourceAsStream);

        if (hiccupSound.getChannelsPerFrame() == 1) {
            synth.add(hiccupPlayer = new VariableRateMonoReader());
            hiccupPlayer.output.connect(0, lineOut.input, 0);
        } else if (hiccupSound.getChannelsPerFrame() == 2) {
            synth.add(hiccupPlayer = new VariableRateStereoReader());
            hiccupPlayer.output.connect(0, lineOut.input, 0);
            hiccupPlayer.output.connect(1, lineOut.input, 1);
        } else {
            throw new RuntimeException("Can only play mono or stereo samples.");
        }
        hiccupPlayer.rate.set(hiccupSound.getFrameRate());

        if (config.napTime > 0) {
            nap(config.napTime);
        }
    }

    public void playMinorGC(GcInfo info) {
        logger.log(Level.FINE, "Minor GC {0}", info);
        minorGcSound.noteOn(300, config.minorGcVolume, synth.createTimeStamp());
    }

    public void playMixedGC(GcInfo info) {
        logger.log(Level.FINE, "Mixed GC {0}", info);
        mixedGcSound.noteOn(200, config.mixedGcVolume, synth.createTimeStamp());
    }

    public void hiccup(long hiccupNs) {
        double millis = hiccupNs / 1e6;
        logger.log(Level.FINE, "Hiccup duration in millis = {0}", millis);
        if (millis > config.hiccupThreshold) {
            hiccupPlayer.dataQueue.queue(hiccupSound);
        }
    }

    public void setAllocTone(double frequency) {
        // https://github.com/philburk/jsyn/blob/master/tests/com/jsyn/examples/ChebyshevSong.java
        // XXX ideally should set this to something that maps it to the closest note
        logger.log(Level.FINE, "Allocation rate = {0} MB/sec", frequency);
        if (frequency < config.allocThreshold) {
            allocTone.amplitude.set(0);
        } else {
            allocTone.frequency.set(frequency);
            allocTone.amplitude.set(config.allocVolume);
        }
    }

    public void start() {
        synth.start();
        allocTone.start();
        lineOut.start();

        hiccupProducer.start();
        allocProducer.start();
        gcEventProducer.start();

        hiccupPlayer.amplitude.set(config.hiccupVolume);
        allocTone.amplitude.set(config.allocVolume);
    }

    public void stop() {
        gcEventProducer.stop();
        allocProducer.stop();
        hiccupProducer.stop();
        lineOut.stop();
        allocTone.stop();
        synth.stop();
    }

    // https://www.fasterj.com/articles/gcnotifs.shtml
    private static class GCEventProducer {
        private final NotificationListener listener = new GCListener();
        private final Consumer<GcInfo> minorConsumer;
        private final Consumer<GcInfo> majorConsumer;

        public GCEventProducer(Consumer<GcInfo> minorConsumer, Consumer<GcInfo> majorConsumer) {
            this.minorConsumer = minorConsumer;
            this.majorConsumer = majorConsumer;
        }

        public void start() {
            List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gcbean : gcbeans) {
                NotificationEmitter emitter = (NotificationEmitter) gcbean;
                emitter.addNotificationListener(listener, null, null);
            }
        }

        public void stop() {
            List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gcbean : gcbeans) {
                NotificationEmitter emitter = (NotificationEmitter) gcbean;
                try {
                    emitter.removeNotificationListener(listener);
                } catch (ListenerNotFoundException e) {
                    // don't care
                }
            }
        }

        class GCListener implements NotificationListener {
            @Override
            public void handleNotification(Notification notification, Object handback) {
                if (notification.getType().equals(GARBAGE_COLLECTION_NOTIFICATION)) {
                    GarbageCollectionNotificationInfo info = from((CompositeData) notification.getUserData());
                    String gctype = info.getGcAction();
                    if ("end of minor GC".equals(gctype)) {
                        minorConsumer.accept(info.getGcInfo());
                    } else {
                        majorConsumer.accept(info.getGcInfo());
                    }
                }
            }
        }
    }

    // https://github.com/clojure-goes-fast/jvm-alloc-rate-meter
    private static class AllocationRateProducer {
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

    // https://github.com/clojure-goes-fast/jvm-hiccup-meter
    private static class HiccupProducer {
        private final jvm_hiccup_meter.MeterThread meterThread;

        public HiccupProducer(Consumer<Long> callback) {
            int resolutionMs = 10;
            meterThread = new jvm_hiccup_meter.MeterThread(callback::accept, resolutionMs);
        }

        public void start() {
            meterThread.start();
        }

        public void stop() {
            meterThread.terminate();
        }
    }

    static class Configuration {
        public long napTime = DEFAULT_NAP_TIME;

        public double allocThreshold = DEFAULT_ALLOC_THRESHOLD;
        public double allocVolume = DEFAULT_ALLOC_VOLUME;

        public double minorGcVolume = DEFAULT_MINOR_GC_VOLUME;
        public double mixedGcVolume = DEFAULT_MIXED_GC_VOLUME;

        public long hiccupThreshold = DEFAULT_HICCUP_THRESHOLD;
        public double hiccupVolume = DEFAULT_HICCUP_VOLUME;

        public void parseArgs(String[] args) {
            for (int i = 0; i < args.length; ++i) {
                if (args[i].equals("-n")) {
                    napTime = Long.parseLong(args[++i]);
                } else if (args[i].equals("-at")) {
                    allocThreshold = Double.parseDouble(args[++i]);
                } else if (args[i].equals("-av")) {
                    allocVolume = Double.parseDouble(args[++i]);
                } else if (args[i].equals("-mv")) {
                    minorGcVolume = Double.parseDouble(args[++i]);
                }else if (args[i].equals("-Mv")) {
                    mixedGcVolume = Double.parseDouble(args[++i]);
                } else if (args[i].equals("-ht")) {
                    hiccupThreshold = Long.parseLong(args[++i]);
                } else if (args[i].equals("-hv")) {
                     hiccupVolume = Double.parseDouble(args[++i]);
                 } else {
                    System.out.println("Usage: " +
                            "[-n napTimeMs] " +
                            "[-at allocThresholdMs] " +
                            "[-av allocVolume 0.0 - 1.0] " +
                            "[-mv minorGCVolume 0.0 - 1.0] " +
                            "[-Mv mixedGCVolume 0.0 - 1.0] " +
                            "[-ht hiccupThresholdMs] " +
                            "[-hv hiccupVolume 0.0 - 1.0] ");

                    System.exit(1);
                }
            }
        }
    }

}
