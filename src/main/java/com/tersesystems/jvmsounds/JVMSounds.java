package com.tersesystems.jvmsounds;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.instruments.DrumWoodFM;
import com.jsyn.instruments.NoiseHit;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.SineOscillator;
import com.sun.management.GcInfo;

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

    private final AllocationRateProducer alloc;
    //private final HiccupProducer hiccup = new HiccupProducer(do something with notes here);

    private final GCEventProducer gc = new GCEventProducer(this::minorGcInstrument, this::majorGcInstrument);

    public JVMSounds() {
        synth = JSyn.createSynthesizer();
        osc = new SineOscillator();
        lineOut = new LineOut();
        alloc = new AllocationRateProducer(this::setFrequency);
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

        setVolume(0.1);
        osc.amplitude.set(0.1);
    }

    public void minorGcInstrument(GcInfo info) {
        drumWoodFM.noteOn(300, 0.5, synth.createTimeStamp());
    }

    public void majorGcInstrument(GcInfo info) {
        noiseHit.noteOn(200, 1.0, synth.createTimeStamp());
    }

    public void setVolume(double volume) {
        osc.amplitude.set(volume);
    }

    public void setFrequency(double frequency) {
        // https://github.com/philburk/jsyn/blob/master/tests/com/jsyn/examples/ChebyshevSong.java
        // XXX ideally should set this to something that maps it to the closest note
        //System.out.println("Allocation rate = " + frequency + " MB/sec");
        osc.frequency.set(frequency);
    }

    public void start() {
        synth.start();
        osc.start();
        lineOut.start();

        alloc.start();
        gc.start();
    }

    public void stop() {
        gc.stop();
        alloc.stop();
        lineOut.stop();
        osc.stop();
        synth.stop();
    }

}
