package com.softbankrobotics.pepper.pepperGPT;

import android.media.audiofx.Visualizer;
import android.util.Log;

/** Frequency bands from the radio player's audio session. */
final class RadioSpectrum implements Visualizer.OnDataCaptureListener {
    private static final String TAG = "PepperRadioSpectrum";
    private static final int[] EDGES_HZ = {40, 85, 130, 195, 300, 450, 675, 1000, 1500, 2300, 3500, 5500, 12000};
    private static final double MAX_LOG_MAGNITUDE = Math.log1p(128);
    private final float[] levels = new float[EDGES_HZ.length - 1];
    private final Listener listener;
    private Visualizer effect;
    private boolean reportedSignal;

    interface Listener {
        void levels(float[] levels);
    }

    RadioSpectrum(Listener listener) {
        this.listener = listener;
    }

    void start(int sessionId) {
        if (effect != null || sessionId <= 0) return;
        try {
            effect = new Visualizer(sessionId);
            int[] sizes = Visualizer.getCaptureSizeRange();
            check(effect.setCaptureSize(sizes[1]));
            check(effect.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED));
            check(effect.setDataCaptureListener(this, Math.min(20000, Visualizer.getMaxCaptureRate()), false, true));
            check(effect.setEnabled(true));
            reportedSignal = false;
            Log.i(TAG, "FFT capture attached to radio session");
        } catch (RuntimeException failure) {
            stop();
            Log.w(TAG, "FFT capture unavailable: " + failure.getClass().getSimpleName());
        }
    }

    private static void check(int status) {
        if (status != Visualizer.SUCCESS) throw new IllegalStateException("Visualizer status " + status);
    }

    void stop() {
        Visualizer previous = effect;
        effect = null;
        if (previous != null) previous.release();
    }

    @Override public void onWaveFormDataCapture(Visualizer source, byte[] samples, int sampleRateMilliHz) {}

    @Override public void onFftDataCapture(Visualizer source, byte[] fft, int sampleRateMilliHz) {
        if (source != effect || sampleRateMilliHz <= 0 || fft.length < 4) return;
        int sampleRate = sampleRateMilliHz / 1000;
        int begin = Math.max(1, (int)Math.ceil(EDGES_HZ[0] * (double)fft.length / sampleRate));
        boolean hasSignal = false;
        for (int band = 0; band < levels.length; band++) {
            int end = Math.min(fft.length / 2,
                    Math.max(begin + 1, (int)Math.ceil(EDGES_HZ[band + 1] * (double)fft.length / sampleRate)));
            int peakSquared = 0;
            for (int bin = begin; bin < end; bin++) {
                int real = fft[2 * bin];
                int imaginary = fft[2 * bin + 1];
                peakSquared = Math.max(peakSquared, real * real + imaginary * imaginary);
            }
            begin = end;
            double magnitude = Math.max(0, Math.sqrt(peakSquared) - 1);
            levels[band] = (float)Math.min(1, Math.log1p(magnitude) / MAX_LOG_MAGNITUDE);
            hasSignal |= magnitude > 0;
        }
        if (hasSignal && !reportedSignal) {
            reportedSignal = true;
            Log.i(TAG, "Nonzero frequency data received");
        }
        listener.levels(levels);
    }
}
