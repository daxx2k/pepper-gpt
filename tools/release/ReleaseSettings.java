package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jcraft.jsch.JSch;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

/** Optional legacy SSH helpers: all configuration is supplied by the installer. */
public final class ReleaseSettings {
    private static final String PREFS = "PepperGPT_Prefs";
    private ReleaseSettings() {}
    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static Context context(Object manager) throws Exception {
        return (Context) field(manager, "context").get(manager);
    }
    public static boolean configure(Object manager) {
        try {
            SharedPreferences prefs = context(manager).getSharedPreferences(PREFS, 0);
            String host = prefs.getString("ssh_host", "").trim();
            String user = prefs.getString("ssh_user", "").trim();
            String password = prefs.getString("ssh_password", "");
            String knownHosts = prefs.getString("ssh_known_hosts", "").trim();
            if (host.isEmpty() || user.isEmpty() || password.isEmpty() || knownHosts.isEmpty()) return false;
            field(manager, "pepperIp").set(manager, host);
            field(manager, "sshUser").set(manager, user);
            field(manager, "sshPassword").set(manager, password);
            return true;
        } catch (Exception error) { return false; }
    }
    public static JSch newJSch(Object owner) throws Exception {
        Object manager = field(owner, "this$0").get(owner);
        String knownHosts = context(manager).getSharedPreferences(PREFS, 0)
                .getString("ssh_known_hosts", "");
        JSch ssh = new JSch();
        ssh.setKnownHosts(new ByteArrayInputStream(knownHosts.getBytes(StandardCharsets.UTF_8)));
        return ssh;
    }
    private static LinearLayout column(View view) {
        if (view instanceof ScrollView && ((ScrollView) view).getChildCount() > 0
                && ((ScrollView) view).getChildAt(0) instanceof LinearLayout)
            return (LinearLayout) ((ScrollView) view).getChildAt(0);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout found = column(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    public static void attach(Activity activity) {
        LinearLayout parent = column(activity.findViewById(android.R.id.content));
        if (parent == null) return;
        Button button = new Button(activity);
        button.setText("Optional robot SSH settings");
        button.setOnClickListener(view -> show(activity));
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    private static EditText input(Activity activity, LinearLayout parent, String label, String value, boolean password) {
        TextView title = new TextView(activity); title.setText(label); parent.addView(title);
        EditText edit = new EditText(activity); edit.setText(value);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        edit.setSingleLine(!label.contains("known_hosts"));
        parent.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }
    private static void show(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, 0);
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(24, 16, 24, 16); body.setFocusableInTouchMode(true);
        TextView help = new TextView(activity);
        help.setText("Optional for legacy LED and battery helpers. Native speech and gestures use QiSDK. "
                + "Enter your robot credentials and a known_hosts line whose host key you have verified through a trusted connection. "
                + "Restart the app after changing these settings. Leave all fields empty to disable SSH.");
        body.addView(help);
        EditText host = input(activity, body, "Robot SSH host", prefs.getString("ssh_host", ""), false);
        EditText user = input(activity, body, "SSH user", prefs.getString("ssh_user", ""), false);
        EditText password = input(activity, body, "SSH password", prefs.getString("ssh_password", ""), true);
        EditText known = input(activity, body, "Verified known_hosts line", prefs.getString("ssh_known_hosts", ""), false);
        ScrollView scroll = new ScrollView(activity); scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Optional robot SSH")
                .setView(scroll).setNegativeButton("Cancel", null)
                .setNeutralButton("Disable SSH", (d, which) -> prefs.edit().remove("ssh_host")
                        .remove("ssh_user").remove("ssh_password").remove("ssh_known_hosts").apply())
                .setPositiveButton("Save", (d, which) -> prefs.edit()
                        .putString("ssh_host", host.getText().toString().trim())
                        .putString("ssh_user", user.getText().toString().trim())
                        .putString("ssh_password", password.getText().toString())
                        .putString("ssh_known_hosts", known.getText().toString().trim()).apply()).create();
        body.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }
}
