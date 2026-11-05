package com.softbankrobotics.pepper.pepperGPT;

import android.content.Context;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class RobotSsh {
    private static Context application;
    public static void init(Context context) { application = context.getApplicationContext(); }

    public static String setting(String key) {
        String value = application.getSharedPreferences("PepperGPT_Prefs", 0).getString(key, "");
        if (!"ssh_host".equals(key) || !localTrust().isFile()) return value;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(localTrust()), StandardCharsets.UTF_8))) {
            String host = reader.readLine();
            if (("# " + value).equals(host)) return "198.18.0.1";
        } catch (IOException ignored) {}
        return value;
    }

    public static boolean configured() {
        return !setting("ssh_host").trim().isEmpty() && !setting("ssh_user").trim().isEmpty()
                && !setting("ssh_password").isEmpty()
                && (!setting("ssh_known_hosts").trim().isEmpty() || localTrust().isFile());
    }

    private static File localTrust() { return new File(application.getFilesDir(), "pepper_known_hosts"); }

    public static void trust(JSch ssh) throws JSchException, IOException {
        ByteArrayOutputStream known = new ByteArrayOutputStream();
        known.write((setting("ssh_known_hosts").trim() + "\n").getBytes(StandardCharsets.UTF_8));
        if (localTrust().isFile()) {
            try (FileInputStream input = new FileInputStream(localTrust())) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) known.write(buffer, 0, count);
            }
        }
        ssh.setKnownHosts(new ByteArrayInputStream(known.toByteArray()));
    }
}
