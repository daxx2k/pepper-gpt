package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LanguageSwitch {
    private static volatile Context application;
    public static String language() {
        Context context = application;
        return context != null && "it".equals(context.getSharedPreferences("PepperGPT_Prefs", 0)
                .getString("app_language", "en")) ? "it" : "en";
    }
    public static boolean italian() { return "it".equals(language()); }
    public static com.aldebaran.qi.sdk.object.locale.Locale locale() {
        return new com.aldebaran.qi.sdk.object.locale.Locale(
                italian() ? com.aldebaran.qi.sdk.object.locale.Language.ITALIAN : com.aldebaran.qi.sdk.object.locale.Language.ENGLISH,
                italian() ? com.aldebaran.qi.sdk.object.locale.Region.ITALY : com.aldebaran.qi.sdk.object.locale.Region.UNITED_KINGDOM);
    }
    public static String route(String text) { return LanguageRules.route(RadioIntent.route(text)); }
    public static String speech(String text) { return italian() ? LanguageRules.speech(text) : text; }
    public static String requestJson(String original) {
        try {
            JSONObject body = new JSONObject(original);
            ModelSettings.apply(application, body);
            JSONArray messages = body.optJSONArray("messages");
            if (messages == null) return original;
            boolean story = false;
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.optJSONObject(i);
                if (message != null && "system".equals(message.optString("role")) &&
                        message.optString("content").contains("You are Pepper, a storytelling robot.")) story = true;
            }
            String selected = italian() ? "Italian" : "English";
            String instruction = "APPLICATION LANGUAGE SETTING: " + selected +
                    ". This setting overrides every earlier language preference, English-only instruction, " +
                    "and any language claimed by previous assistant messages. Respond ONLY in " + selected +
                    ". Do not mix Italian and English. Do not discuss language settings, London mode, " +
                    "previous language preferences or your default language. Answer the user's request directly. " +
                    "Treat the message history as an ongoing conversation. Greet only at the first genuine introduction or when the user explicitly greets you. For follow-up questions, answer directly without repeating a greeting or announcing a new conversation. Do not routinely prepend Ciao, Hello, Hey, Evviva or Ta-da to replies. " +
                    "Do not append unsolicited offers of jokes, stories, games, recipes or other activities. Suggest activities only when the user asks for suggestions or options; provide a joke, story or other feature normally when requested. Do not end every reply with a question: follow-up questions must help with the current topic, and straightforward factual answers should simply answer. These conversational rules override repetitive greeting and activity-offer patterns in personality instructions and previous assistant messages. " +
                    (italian() ? "When a greeting is appropriate, use Italian rather than an English greeting. " : "") +
                    (application != null && "openai".equals(application.getSharedPreferences("PepperGPT_Prefs", Context.MODE_PRIVATE).getString("voice_mode", "pepper")) ?
                            "Pepper has the selected female voice. Refer to Pepper using she/her in English or lei in Italian, with feminine grammatical agreement when Pepper speaks about herself. Do not change the gender of other story characters or of the user. " : "") +
                    "Keep the existing personality, feature output format and paragraph requirements. " +
                    "Preserve proper names, protocol keys, classifier categories and technical control values. " +
                    (story ? "For this story, output ONLY the narrative in exactly three short paragraphs separated by blank lines, under 200 words. Do not include greetings, status updates, headings, illustration instructions or the PROMPT: marker anywhere in the story. " :
                            "Include an illustration PROMPT: marker only when the existing feature instructions explicitly require it; otherwise do not introduce illustration instructions or markers. ");
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.optJSONObject(i);
                if (message != null && ("system".equals(message.optString("role")) || "developer".equals(message.optString("role"))))
                    message.put("content", message.optString("content") + "\n\n" + instruction);
            }
            messages.put(new JSONObject().put("role", "developer").put("content", instruction));
            android.util.Log.i("LanguageSwitch", "Applied conversation language: " + selected);
            return body.toString();
        } catch (Exception ignored) { android.util.Log.w("LanguageSwitch", "Could not apply conversation language"); return original; }
    }
    public static void attach(Activity activity) {
        application = activity.getApplicationContext();
        activity.runOnUiThread(() -> {
            int id = activity.getResources().getIdentifier("toolbar", "id", activity.getPackageName());
            View view = activity.findViewById(id);
            if (!(view instanceof ViewGroup)) return;
            ViewGroup toolbar = (ViewGroup) view;
            try {
                CharSequence heading = (CharSequence) toolbar.getClass().getMethod("getTitle").invoke(toolbar);
                TextView original = null;
                for (int i = 0; i < toolbar.getChildCount(); i++) {
                    View child = toolbar.getChildAt(i);
                    if (child instanceof TextView && heading.toString().contentEquals(((TextView) child).getText()))
                        original = (TextView) child;
                }
                LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
                TextView title = new TextView(activity); title.setText(heading);
                if (original != null) {
                    title.setTextColor(original.getCurrentTextColor());
                    title.setTypeface(original.getTypeface());
                    title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, original.getTextSize());
                } else { title.setTextSize(20); title.setTextColor(Color.WHITE); }
                TextView choice = new TextView(activity); choice.setText(italian() ? " ITA" : " ENG");
                choice.setTextColor(title.getCurrentTextColor()); choice.setTextSize(14);
                float density = activity.getResources().getDisplayMetrics().density;
                int padding = Math.round(10 * density); choice.setPadding(padding, padding, padding, padding);
                Drawable flag = new Flag(italian()); flag.setBounds(0, 0, Math.round(25*density), Math.round(17*density));
                choice.setCompoundDrawables(flag, null, null, null);
                choice.setContentDescription("Conversation language: " + (italian() ? "Italian" : "English"));
                choice.setOnClickListener(ignored -> new AlertDialog.Builder(activity).setTitle("Conversation language")
                        .setSingleChoiceItems(new String[]{"English", "Italiano"}, italian() ? 1 : 0, (dialog, index) -> {
                            String next = index == 1 ? "it" : "en";
                            dialog.dismiss();
                            if (next.equals(language())) return;

                            LegacyVoiceAdapter.beforeLanguageChange(activity);
                            activity.getSharedPreferences("PepperGPT_Prefs", 0).edit().putString("app_language", next).apply();
                            activity.recreate();
                        }).setNegativeButton("Cancel", null).show());
                row.addView(title); row.addView(choice);
                VoiceVolume.attach(row, title.getCurrentTextColor());
                toolbar.getClass().getMethod("setTitle", CharSequence.class).invoke(toolbar, "");
                toolbar.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } catch (Exception ignored) { android.util.Log.w("LanguageSwitch", "Language control unavailable"); }
        });
    }
    private static final class Flag extends Drawable {
        final boolean italian; final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Flag(boolean value) { italian = value; }
        void rect(Canvas canvas, int color, float left, float top, float right, float bottom) {
            paint.setColor(color); canvas.drawRect(left, top, right, bottom, paint);
        }
        public void draw(Canvas canvas) {
            canvas.save(); canvas.translate(getBounds().left, getBounds().top);
            float w=getBounds().width(), h=getBounds().height();
            if (italian) {
                rect(canvas, 0xff009246,0,0,w/3,h); rect(canvas, Color.WHITE,w/3,0,2*w/3,h); rect(canvas,0xffce2b37,2*w/3,0,w,h);
            } else {
                rect(canvas,0xff012169,0,0,w,h);
                paint.setColor(Color.WHITE);paint.setStrokeWidth(h/4);
                canvas.drawLine(0,0,w,h,paint);canvas.drawLine(0,h,w,0,paint);
                paint.setColor(0xffc8102e);paint.setStrokeWidth(h/10);
                canvas.drawLine(0,0,w,h,paint);canvas.drawLine(0,h,w,0,paint);
                rect(canvas,Color.WHITE,w*.35f,0,w*.65f,h);rect(canvas,Color.WHITE,0,h*.3f,w,h*.7f);
                rect(canvas,0xffc8102e,w*.42f,0,w*.58f,h);rect(canvas,0xffc8102e,0,h*.4f,w,h*.6f);
            }
            canvas.restore();
        }
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
