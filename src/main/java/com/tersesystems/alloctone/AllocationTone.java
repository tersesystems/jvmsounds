package com.tersesystems.alloctone;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.SineOscillator;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * Code taken from https://github.com/philburk/jsyn/blob/master/tests/com/jsyn/examples/PlayTone.java
 */
public class AllocationTone {

    private final Synthesizer synth = JSyn.createSynthesizer();
    private final SineOscillator osc = new SineOscillator();
    private final LineOut lineOut = new LineOut();

    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("tone-scheduled-service");
        return t;
    });

    public AllocationTone() {
        synth.add(osc);
        synth.add(lineOut);
        osc.output.connect(0, lineOut.input, 0);
        osc.output.connect(0, lineOut.input, 1);

        osc.amplitude.set(0.6);
    }

    public void setVolume(double volume) {
        osc.amplitude.set(volume);
    }

    public void start(DoubleSupplier supplier, Duration interval) {
        synth.start();
        osc.start();

        // We only need to start the LineOut. It will pull data from the
        // oscillator.
        lineOut.start();

        Runnable updateFunction = () -> osc.frequency.set(supplier.getAsDouble());
        ses.scheduleAtFixedRate(updateFunction, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        ses.shutdown();
        osc.stop();
        lineOut.stop();
        synth.stop();
    }

}
