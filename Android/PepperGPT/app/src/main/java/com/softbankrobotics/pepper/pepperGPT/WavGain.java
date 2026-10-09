package com.softbankrobotics.pepper.pepperGPT;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Normalize generated 16-bit PCM only; leave timing, headers and other audio untouched. */
public final class WavGain {
    private WavGain() { }
    private static int le32(RandomAccessFile file) throws IOException {
        return Integer.reverseBytes(file.readInt());
    }
    public static boolean normalize(File path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "rw")) {
            if (file.length() < 44 || file.readInt() != 0x52494646) return false;
            file.seek(8); if (file.readInt() != 0x57415645) return false;
            int bits = 0, format = 0; long start = -1; int size = 0;
            while (file.getFilePointer() + 8 <= file.length()) {
                int tag = file.readInt(), length = le32(file);
                if (length < 0 || file.getFilePointer() + length > file.length()) return false;
                long next = file.getFilePointer() + length + (length & 1);
                if (tag == 0x666d7420 && length >= 16) {
                    format = Short.reverseBytes(file.readShort()) & 0xffff;
                    file.skipBytes(12); bits = Short.reverseBytes(file.readShort()) & 0xffff;
                } else if (tag == 0x64617461) { start = file.getFilePointer(); size = length; break; }
                file.seek(next);
            }
            if (format != 1 || bits != 16 || start < 0 || size == 0 || size > 16 * 1024 * 1024 || (size & 1) != 0) return false;
            byte[] pcm = new byte[size]; file.seek(start); file.readFully(pcm);
            int peak = 0;
            for (int i = 0; i < size; i += 2) peak = Math.max(peak, Math.abs((pcm[i] & 255) | (pcm[i + 1] << 8)));
            if (peak == 0) return true;
            double gain = Math.min(4.0, 30000.0 / peak);
            if (gain <= 1.0) return true;
            for (int i = 0; i < size; i += 2) {
                int sample = (pcm[i] & 255) | (pcm[i + 1] << 8);
                int boosted = (int) Math.round(sample * gain);
                boosted = Math.max(-32768, Math.min(32767, boosted));
                pcm[i] = (byte) boosted; pcm[i + 1] = (byte) (boosted >> 8);
            }
            file.seek(start); file.write(pcm);
            return true;
        }
    }
}
