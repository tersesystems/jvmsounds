package com.tersesystems.alloctone;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * Plays a continously changing tone from a supplier.
 *
 * The sample player code is taken from the
 * <a href="https://www.drdobbs.com/jvm/music-components-in-java-creating-oscill/230500178">Music Components in Java: Creating Oscillators</a>
 * article in Dr Dobbs, written by Craig Lindley.
 */
public class SoftSynth {
    private final ScheduledExecutorService st = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r);
        t.setName("alloctone-scheduled-daemon");
        t.setDaemon(true);
        return t;
    });

    private final SamplePlayer player = new SamplePlayer();

    public SoftSynth(DoubleSupplier supplier) {
        this(supplier, Duration.ofMillis(50));
    }

    public SoftSynth(DoubleSupplier supplier, Duration interval) {
        BasicOscillator osc = new BasicOscillator();
        osc.setWaveshape(BasicOscillator.WAVESHAPE.SIN);

        Runnable runnable = () -> {
            double frequency = supplier.getAsDouble();
            osc.setFrequency(frequency);
        };
        start(osc, runnable, interval);
    }

    void start(ISampleProvider sampleProvider, Runnable runnable, Duration interval) {
        st.scheduleAtFixedRate(runnable, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        player.setSampleProvider(sampleProvider);
        player.startPlayer();
    }

    public void stop() {
        st.shutdown();
        player.stopPlayer();
    }

    public interface ISampleProvider {
        int getSamples(byte[] buffer);
    }

    public static class BasicOscillator implements ISampleProvider {
        private BasicOscillator.WAVESHAPE waveshape;
        private long periodSamples;
        private long sampleNumber;

        public enum WAVESHAPE {
            SIN, SQU, SAW
        }

        public BasicOscillator() {
            setWaveshape(BasicOscillator.WAVESHAPE.SIN);
            setFrequency(1000.0);
        }

        public void setWaveshape(BasicOscillator.WAVESHAPE waveshape) {
            this.waveshape = waveshape;
        }

        public void setFrequency(double frequency) {
            periodSamples = (long) (SamplePlayer.SAMPLE_RATE / frequency);
        }

        protected double getSample() {
            double value;
            double x = sampleNumber / (double) periodSamples;

            switch (waveshape) {
                default:

                case SIN:
                    value = Math.sin(2.0 * Math.PI * x);
                    break;

                case SQU:
                    if (sampleNumber < (periodSamples / 2)) {
                        value = 1.0;
                    } else {
                        value = -1.0;
                    }
                    break;

                case SAW:
                    value = 2.0 * (x - Math.floor(x + 0.5));
                    break;
            }
            sampleNumber = (sampleNumber + 1) % periodSamples;
            return value;
        }

        public int getSamples(byte[] buffer) {
            int index = 0;
            for (int i = 0; i < SamplePlayer.SAMPLES_PER_BUFFER; i++) {
                double ds = getSample() * Short.MAX_VALUE;
                short ss = (short) Math.round(ds);
                buffer[index++] = (byte) (ss >> 8);
                buffer[index++] = (byte) (ss & 0xFF);
            }
            return SamplePlayer.BUFFER_SIZE;
        }
    }

    public static class SamplePlayer extends Thread implements ISampleProvider {
        // Count of zeroed buffers to return before switching to real sample provider
        private static final int TEMP_BUFFER_COUNT = 20;

        // AudioFormat parameters
        public static final int SAMPLE_RATE = 22050;
        private static final int SAMPLE_SIZE = 16;
        private static final int CHANNELS = 1;
        private static final boolean SIGNED = true;
        private static final boolean BIG_ENDIAN = true;

        // Chunk of audio processed at one time
        public static final int BUFFER_SIZE = 1000;
        public static final int SAMPLES_PER_BUFFER = BUFFER_SIZE / 2;

        // Instance data
        private final AudioFormat format;
        private final DataLine.Info info;
        private SourceDataLine auline;
        private boolean hasRun;
        private boolean done;
        private int bufferCount;
        private final byte[] sampleData = new byte[BUFFER_SIZE];
        private ISampleProvider provider;
        private ISampleProvider realProvider;

        public SamplePlayer() {
            format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, SIGNED, BIG_ENDIAN);
            info = new DataLine.Info(SourceDataLine.class, format);
            Arrays.fill(sampleData, (byte) 0);
            // Set temp provider so zeroed buffers are consumed initially
            provider = this;
        }

        /**
         * Process a buffer full of samples pulled from the sample provider
         * <p>
         * NOTE: this class acts as a sample provider to itself during<br>
         * the initialization of the sound engine. Once bufferCount of buffers<br>
         * has been processed, SamplePlayer switches to its real sample provider.<br>
         * This is necessary to prevent glitches on startup.
         *
         * @param buffer Buffer in which the samples are to be processed
         * @return Count of number of bytes processed
         */
        public int getSamples(byte[] buffer) {
            bufferCount++;
            if (bufferCount >= TEMP_BUFFER_COUNT) {
                // Audio system flushed so switch to real sample provider
                provider = realProvider;
            }
            return BUFFER_SIZE;
        }

        public void run() {
            done = false;
            int nBytesRead = 0;
            try {
                // Get line to write data to
                auline = (SourceDataLine) AudioSystem.getLine(info);
                auline.open(format);
                auline.start();

                while ((nBytesRead != -1) && (!done)) {
                    nBytesRead = provider.getSamples(sampleData);
                    if (nBytesRead > 0) {
                        auline.write(sampleData, 0, nBytesRead);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                auline.drain();
                auline.close();
            }
        }

        public void startPlayer() {
            if (hasRun) {
                System.err.println("Illegal to restart a Thread once it has been stopped");
                return;
            }
            if (realProvider != null) {
                hasRun = true;
                start();
            }
        }

        public void stopPlayer() {
            done = true;
        }

        public void setSampleProvider(ISampleProvider provider) {
            realProvider = provider;
        }
    }
}
