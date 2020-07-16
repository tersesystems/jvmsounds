package com.tersesystems.jvmsounds;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.instruments.DrumWoodFM;
import com.jsyn.instruments.NoiseHit;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.SineOscillator;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.function.Consumer;

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

    private final Synthesizer synth;
    private final SineOscillator osc;
    private final LineOut lineOut;

    private final DrumWoodFM drumWoodFM;
    private final NoiseHit noiseHit;

    private final HiccupProducer hiccup;
    private final AllocationRateProducer alloc;
    private final GCEventProducer gc;

    public JVMSounds() {
        synth = JSyn.createSynthesizer();
        osc = new SineOscillator();
        lineOut = new LineOut();
        drumWoodFM = new DrumWoodFM();
        noiseHit = new NoiseHit();

        synth.add(osc);
        synth.add(lineOut);
        synth.add(drumWoodFM);
        synth.add(noiseHit);

        osc.output.connect(0, lineOut.input, 0);
        osc.output.connect(0, lineOut.input, 1);

        drumWoodFM.getOutput().connect(0, lineOut.input, 0);
        drumWoodFM.getOutput().connect(0, lineOut.input, 1);

        noiseHit.getOutput().connect(0, lineOut.input, 0);
        noiseHit.getOutput().connect(0, lineOut.input, 1);

        setAllocVolume(0.1);

        alloc = new AllocationRateProducer(this::setAllocTone);
        gc = new GCEventProducer(this::minorGcInstrument, this::majorGcInstrument);
        hiccup = new HiccupProducer(this::hiccup);
    }

    public void minorGcInstrument(GcInfo info) {
        drumWoodFM.noteOn(300, 0.5, synth.createTimeStamp());
    }

    public void majorGcInstrument(GcInfo info) {
        noiseHit.noteOn(200, 1.0, synth.createTimeStamp());
    }

    public void hiccup(long hiccupNs) {
        // XXX add hiccup sample here
        // System.out.println("Hiccup duration = " + hiccupNs);
    }

    public void setAllocVolume(double volume) {
        osc.amplitude.set(volume);
    }

    public void setAllocTone(double frequency) {
        // https://github.com/philburk/jsyn/blob/master/tests/com/jsyn/examples/ChebyshevSong.java
        // XXX ideally should set this to something that maps it to the closest note
        //System.out.println("Allocation rate = " + frequency + " MB/sec");
        osc.frequency.set(frequency);
    }

    public void start() {
        synth.start();
        osc.start();
        lineOut.start();

        hiccup.start();
        alloc.start();
        gc.start();
    }

    public void stop() {
        gc.stop();
        alloc.stop();
        hiccup.stop();
        lineOut.stop();
        osc.stop();
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
        private final jvm_alloc_rate_meter.MeterThread meterThread;

        public AllocationRateProducer(Consumer<Double> rateConsumer) {
            int intervalMs = 50;
            meterThread = new jvm_alloc_rate_meter.MeterThread(bytes -> {
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
}
