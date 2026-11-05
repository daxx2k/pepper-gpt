package com.softbankrobotics.pepper.pepperGPT;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/** Holds streamed PCM while the speaker plays earlier samples in order. */
final class PcmQueue {
    // About three minutes of 24 kHz, 16-bit mono audio. The second limit bounds
    // the number of allocations if the service sends unusually small packets.
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CHUNKS = 16 * 1024;
    private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
    private int bytes;

    synchronized boolean offer(byte[] pcm) {
        // The zero-length end marker always fits, even if the audio is full.
        if (pcm.length != 0 && (pcm.length > MAX_BYTES - bytes || chunks.size() >= MAX_CHUNKS))
            return false;
        chunks.addLast(pcm);
        bytes += pcm.length;
        notifyAll();
        return true;
    }

    synchronized byte[] poll() {
        byte[] pcm = chunks.pollFirst();
        if (pcm != null) bytes -= pcm.length;
        return pcm;
    }

    synchronized byte[] poll(long timeout, TimeUnit unit) throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        long deadline = System.nanoTime() + remaining;
        while (chunks.isEmpty() && remaining > 0) {
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
            remaining = deadline - System.nanoTime();
        }
        return poll();
    }

    synchronized void clear() {
        chunks.clear();
        bytes = 0;
    }
}
