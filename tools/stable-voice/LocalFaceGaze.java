package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.util.Log;
import android.os.SystemClock;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.LookAtBuilder;
import com.aldebaran.qi.sdk.builder.TransformBuilder;
import com.aldebaran.qi.sdk.object.actuation.Frame;
import com.aldebaran.qi.sdk.object.actuation.FreeFrame;
import com.aldebaran.qi.sdk.object.actuation.LookAt;
import com.aldebaran.qi.sdk.object.actuation.LookAtMovementPolicy;
import com.aldebaran.qi.sdk.object.geometry.Vector3;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Local head-camera fallback, active only while the conversation owns focus. */
final class LocalFaceGaze {
    private final Activity activity;
    private final QiContext qi;
    private volatile Future<Void> movement;
    private volatile int generation;
    private Thread worker;
    private long retryAfter;

    LocalFaceGaze(Activity activity, QiContext qi) {
        this.activity = activity;
        this.qi = qi;
    }

    synchronized void resume() {
        if (worker != null || SystemClock.elapsedRealtime() < retryAfter) return;
        Session connection = RobotLights.currentConnection();
        if (connection == null || !connection.isConnected()) return;
        int token = generation;
        worker = new Thread(() -> track(connection, token), "PepperGPT-face");
        worker.start();
    }

    synchronized void pause() {
        generation++;
        stopMovement();
    }

    private synchronized void stopMovement() {
        if (movement != null) {
            movement.requestCancellation();
            movement = null;
        }
    }

    private void track(Session connection, int token) {
        ChannelExec channel = null;
        try {
            String program;
            try (InputStream source = activity.getAssets().open("pepper_idle/face-gaze.py")) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = source.read(buffer)) != -1) data.write(buffer, 0, read);
                program = new String(data.toByteArray(), StandardCharsets.UTF_8);
            }
            if (generation != token) return;
            channel = (ChannelExec) connection.openChannel("exec");
            channel.setCommand("PYTHONPATH=/opt/aldebaran/lib/python2.7/site-packages "
                    + "LD_LIBRARY_PATH=/opt/aldebaran/lib python -u -c '"
                    + program.replace("'", "'\\''") + "'");
            try (OutputStream pipe = channel.getOutputStream(); InputStream status = channel.getInputStream()) {
                channel.connect(2000);
                Log.i("PepperGaze", "Local face tracker connected");
                Frame gazeFrame = qi.getActuation().gazeFrame();
                FreeFrame target = qi.getMapping().makeFreeFrame();
                StringBuilder line = new StringBuilder();
                long lastFace = 0;
                LookAtMovementPolicy policy = LookAtMovementPolicy.HEAD_AND_BASE;
                while (generation == token && !channel.isClosed()) {
                    pipe.write('.');
                    pipe.flush();
                    String latest = null;
                    while (status.available() > 0 && line.length() < 80) {
                        int character = status.read();
                        if (character < 0) break;
                        if (character == '\n') {
                            latest = line.toString();
                            line.setLength(0);
                        } else line.append((char)character);
                    }
                    if (line.length() >= 80) break;
                    if (latest != null && latest.matches("-?0\\.[0-9]{3} -?0\\.[0-9]{3}")) {
                        String[] offsets = latest.split(" ");
                        double horizontal = Double.parseDouble(offsets[0]);
                        double vertical = Double.parseDouble(offsets[1]);
                        if (Math.abs(horizontal) <= .40 && Math.abs(vertical) <= .38) {
                            target.update(gazeFrame, TransformBuilder.create().fromTranslation(
                                    new Vector3(1.0, -.95 * horizontal, -.72 * vertical)), 0L);
                            lastFace = SystemClock.elapsedRealtime();
                            synchronized (this) {
                                if (generation != token) break;
                                if (movement != null && movement.isDone()) {
                                    if (movement.hasError()) policy = LookAtMovementPolicy.HEAD_ONLY;
                                    movement = null;
                                }
                            }
                            if (movement == null) {
                                LookAt look = LookAtBuilder.with(qi).withFrame(target.frame()).build();
                                look.setPolicy(policy);
                                synchronized (this) {
                                    if (generation != token) break;
                                    movement = look.async().run();
                                    Log.i("PepperGaze", "Face look-at requested: " + policy);
                                }
                            }
                        }
                    }
                    if (SystemClock.elapsedRealtime() - lastFace > 800) stopMovement();
                    Thread.sleep(150);
                }
            }
        } catch (Exception e) {
            Log.w("PepperGaze", "Local face tracker unavailable: " + e.getClass().getSimpleName());
        } finally {
            stopMovement();
            if (channel != null) {
                try { for (int i = 0; i < 8 && !channel.isClosed(); i++) Thread.sleep(40); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                channel.disconnect();
            }
            synchronized (this) {
                worker = null;
                retryAfter = SystemClock.elapsedRealtime() + 1000;
            }
        }
    }
}
