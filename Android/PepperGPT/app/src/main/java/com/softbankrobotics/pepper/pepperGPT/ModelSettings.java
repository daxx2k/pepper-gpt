package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runtime model selection; existing installations retain their original defaults. */
public final class ModelSettings {
    private static final String PREFS = "PepperGPT_Prefs";
    private static final String[] KEYS = {"chat_model", "creative_model", "vision_model"};
    private static final String[] LABELS = {"Chat model", "Stories and recipes model", "Image analysis model"};
    private static final String[] DEFAULTS = {"gpt-5-nano", "gpt-5-mini", "gpt-5-mini"};

    public static String model(Context context, int category) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEYS[category], DEFAULTS[category]);
        return value == null || value.trim().isEmpty() ? DEFAULTS[category] : value.trim();
    }

    public static void apply(Context context, JSONObject body) throws org.json.JSONException {
        if (context == null) return;
        String original = body.optString("model");
        int category;
        if ("gpt-5-nano".equals(original)) category = 0;
        else if ("gpt-5-mini".equals(original)) category = hasImage(body.optJSONArray("messages")) ? 2 : 1;
        else return; // Image generation, transcription and speech keep their own models.
        String selected = model(context, category);
        body.put("model", selected);
        // Legacy reasoning values are model-specific. For a custom model, use its API default.
        if (!original.equals(selected)) body.remove("reasoning_effort");
    }

    private static boolean hasImage(JSONArray messages) {
        if (messages == null) return false;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            JSONArray content = message == null ? null : message.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "image_url".equals(part.optString("type"))) return true;
            }
        }
        return false;
    }

    public static void attach(Activity activity) {
        LinearLayout column = column(activity.findViewById(android.R.id.content));
        if (column == null || column.findViewWithTag("pepper-model-settings") != null) return;
        Button button = new Button(activity);
        button.setTag("pepper-model-settings");
        button.setText("AI models");
        button.setOnClickListener(view -> edit(activity));
        column.addView(button, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void edit(Activity activity) {
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setFocusableInTouchMode(true);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding / 2, padding, padding / 2);
        EditText[] inputs = new EditText[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            TextView label = new TextView(activity);
            label.setText(LABELS[i]);
            fields.addView(label);
            EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setText(model(activity, i));
            input.setContentDescription(LABELS[i]);
            fields.addView(input);
            inputs[i] = input;
        }
        TextView help = new TextView(activity);
        help.setText("Enter an exact OpenAI model ID compatible with Chat Completions. Image analysis also requires image input support. Availability and cost depend on your API account. Voice and generated images use separate models.");
        fields.addView(help);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("AI models").setView(scroll)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null)
                .setNeutralButton("Restore defaults", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                for (int i = 0; i < inputs.length; i++) inputs[i].setText(DEFAULTS[i]);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String[] values = new String[inputs.length];
                for (int i = 0; i < inputs.length; i++) {
                    values[i] = inputs[i].getText().toString().trim();
                    if (values[i].isEmpty()) values[i] = DEFAULTS[i];
                    if (!values[i].matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}")) {
                        inputs[i].setError("Enter a model ID without spaces");
                        inputs[i].requestFocus();
                        return;
                    }
                }
                SharedPreferences.Editor editor = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
                for (int i = 0; i < values.length; i++) {
                    if (DEFAULTS[i].equals(values[i])) editor.remove(KEYS[i]);
                    else editor.putString(KEYS[i], values[i]);
                }
                editor.apply();
                dialog.dismiss();
            });
        });
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }

    private static LinearLayout column(View view) {
        if (view instanceof ScrollView && ((ScrollView) view).getChildCount() > 0 &&
                ((ScrollView) view).getChildAt(0) instanceof LinearLayout)
            return (LinearLayout) ((ScrollView) view).getChildAt(0);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            LinearLayout found = column(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
