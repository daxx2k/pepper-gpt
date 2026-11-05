package com.softbankrobotics.pepper.pepperGPT;

import android.util.Log;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class RobotLights {
    private static volatile Session connection;
    private static Object owner;
    private static volatile String phase = "idle";
    private static Thread worker;

    static Session currentConnection() { return connection; }

    static void connection(Session session) { connection = session; }

    static synchronized void show(Object token, String state) {
        owner = token;
        phase = state;
        if (worker == null) {
            worker = new Thread(RobotLights::send, "PepperGPT-LEDs");
            worker.start();
        }
    }

    static synchronized void clear(Object token) {
        if (owner == token) {
            owner = null;
            phase = "idle";
        }
    }

    private static void send() {
        ChannelExec channel = null;
        OutputStream pipe = null;
        long idleSince = 0;
        String sent = null;
        try {
            while (true) {
                String desired = phase;
                long now = android.os.SystemClock.elapsedRealtime();
                if ("idle".equals(desired)) {
                    if (idleSince == 0) idleSince = now;
                    if (now - idleSince > 800) break;
                } else idleSince = 0;
                if (channel == null) {
                    Session ssh = connection;
                    if (ssh == null || !ssh.isConnected()) {
                        Thread.sleep(250);
                        continue;
                    }
                    channel = (ChannelExec)ssh.openChannel("exec");
                    channel.setCommand(RobotLightProgram.COMMAND);
                    channel.setOutputStream(discard());
                    channel.setErrStream(discard());
                    pipe = channel.getOutputStream();
                    channel.connect(2500);
                }
                if (channel.isClosed()) throw new IllegalStateException("LED channel closed");
                pipe.write((desired + "\n").getBytes(StandardCharsets.US_ASCII));
                pipe.flush();
                if (!desired.equals(sent)) {
                    Log.i("PepperLights", "LED state sent: " + desired);
                    sent = desired;
                }
                Thread.sleep(180);
            }
        } catch (Exception e) {
            Log.w("PepperLights", "LED command interrupted: " + e.getClass().getSimpleName());
        } finally {
            if (pipe != null) try {
                pipe.write("close\n".getBytes(StandardCharsets.US_ASCII));
                pipe.flush();
                pipe.close();
                if (channel != null) for (int i = 0; i < 10 && !channel.isClosed(); i++) Thread.sleep(40);
            } catch (Exception ignored) {}
            if (channel != null) channel.disconnect();
            synchronized (RobotLights.class) {
                worker = null;
            }
        }
    }

    private static OutputStream discard() {
        return new OutputStream() {
            @Override public void write(int value) {}
            @Override public void write(byte[] bytes, int offset, int length) {}
        };
    }
}
