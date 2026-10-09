package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Semantic fallback for radio requests; delegates to the existing radio handler. */
public final class RadioIntent {
    private static final String FUNCTION = "pepper_reply";

    public static boolean fastCommand(String text) {
        // Only unmistakable short commands bypass semantic understanding.
        return text != null && text.toLowerCase(java.util.Locale.ROOT).trim()
                .matches("(?:play|stop) (?:radio|music)(?: (?:pop|synthwave))?[.!?]*");
    }

    public static void configure(JSONObject request) throws JSONException {
        JSONObject properties = new JSONObject()
                .put("action", new JSONObject().put("type", "string").put("enum", new JSONArray().put("none").put("play").put("stop")))
                .put("reply", new JSONObject().put("type", "string").put("description", "Your natural conversational answer for action none; empty string for play/stop, which the application performs."))
                .put("station", new JSONObject().put("type", "string").put("enum", new JSONArray().put("default").put("pop").put("synthwave")));
        JSONObject parameters = new JSONObject().put("type", "object").put("properties", properties)
                .put("required", new JSONArray().put("action").put("station").put("reply")).put("additionalProperties", false);
        JSONObject function = new JSONObject().put("name", FUNCTION).put("strict", true)
                .put("description", "Respond to the user or operate Pepper's radio. Choose action play when the latest user utterance requests listening to music/radio, even indirectly, informally or with speech transcription errors. A request for a musical background is playback, not a preference discussion. Choose station default whenever no specific supported choice is named; do not ask the user to choose a station for a generic playback request. Choose pop for pop music, synthwave for Synthwave, and stop for an affirmative request to stop music. Supported automatic choices are default/Synthwave (SomaFM) and Top 40 Pop. For factual questions, past events, general preferences, ordinary conversation, negated start requests, or unsupported named stations/artists: action none, station default, and a natural reply. Do not start playback merely because radio/music is mentioned. Never claim playback has started/stopped in an action none reply. Existing story/recipe/weather/image modules are separate.")
                .put("parameters", parameters);
        request.put("tools", new JSONArray().put(new JSONObject().put("type", "function").put("function", function)))
                .put("tool_choice", "required").put("parallel_tool_calls", false);
        JSONArray messages = request.optJSONArray("messages");
        if (messages != null) messages.put(new JSONObject().put("role", "developer").put("content",
                "APPLICATION CAPABILITY: Use pepper_reply for every reply. Classify ONLY the final user message, never an earlier request in conversation history. Previous requests are historical, already addressed, and must not be executed now. Classify the latest user's intent: real requested music/radio playback -> action play; requested stopping -> stop; otherwise -> none with your normal conversational reply. Informal or imperfectly transcribed listening requests should work without fixed phrases. For a generic request, select station default and perform it immediately; never pretend to execute it in text or ask for a station unnecessarily. Do not offer activities unprompted. No playback for merely discussing music or a negated start command. For none, preserve all existing personality and conversation instructions; for play/stop, reply is empty."));
        if (messages != null) {
            JSONArray ordered = new JSONArray();
            for (int i = 0; i < messages.length(); i++) {
                JSONObject item = messages.optJSONObject(i);
                if (item != null && ("system".equals(item.optString("role")) || "developer".equals(item.optString("role")))) ordered.put(item);
            }
            for (int i = 0; i < messages.length(); i++) {
                JSONObject item = messages.optJSONObject(i);
                if (item != null && !"system".equals(item.optString("role")) && !"developer".equals(item.optString("role"))) ordered.put(item);
            }
            request.put("messages", ordered);
        }
    }

    public static String command(JSONObject response) {
        try {
            JSONArray choices = response.optJSONArray("choices");
            JSONObject message = choices == null || choices.length() == 0 ? null : choices.getJSONObject(0).optJSONObject("message");
            JSONArray calls = message == null ? null : message.optJSONArray("tool_calls");
            if (calls == null || calls.length() != 1) return null;
            JSONObject function = calls.getJSONObject(0).getJSONObject("function");
            if (!FUNCTION.equals(function.optString("name"))) return null;
            JSONObject arguments = new JSONObject(function.getString("arguments"));
            if (arguments.length() != 3) return null;
            String action = arguments.getString("action"), station = arguments.getString("station");
            if (!"default".equals(station) && !"pop".equals(station) && !"synthwave".equals(station)) return null;
            if ("stop".equals(action)) return "stop radio";
            if (!"play".equals(action)) return null;
            return "play radio" + ("default".equals(station) ? "" : " " + station);
        } catch (JSONException ignored) { return null; }
    }

    private static final class Pending {
        final String input, command;
        final long created = android.os.SystemClock.elapsedRealtime();
        Pending(String text, String value) { input = text; command = value; }
    }
    private static volatile Pending pending;

    public static String route(String text) {
        Pending result = pending;
        return result != null && result.input.equals(text)
                && android.os.SystemClock.elapsedRealtime() - result.created < 30000 ? result.command : text;
    }

    public static boolean candidate(String text) {
        return text != null && java.util.regex.Pattern.compile(
                "\\b(?:radio\\w*|music\\w*|musical\\w*|ascolt\\w*|sentir\\w*|suon\\w*|station\\w*|stazion\\w*|listen\\w*|hear\\w*|songs?|tunes?|synthwave|nightride|pop|sottofondo|playlist)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    public static boolean isRadioRequest(Activity activity, String text, String normalized) {
        pending = null;
        if (fastCommand(normalized)) return true;
        if (!candidate(text) || android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return false;
        if (activity.isFinishing() || !activity.hasWindowFocus()) return false;
        android.content.SharedPreferences prefs = activity.getSharedPreferences("PepperGPT_Prefs", 0);
        String key = prefs.getString("openai_api_key", "");
        if (key == null || key.trim().isEmpty()) return false;
        java.net.HttpURLConnection connection = null;
        try {
            JSONObject request = new JSONObject().put("model", "gpt-5-nano").put("max_completion_tokens", 1024)
                    .put("reasoning_effort", "low").put("messages", new JSONArray()
                    .put(new JSONObject().put("role", "system").put("content", NEUTRAL_INSTRUCTION))
                    .put(new JSONObject().put("role", "user").put("content", text)));
            ModelSettings.apply(activity, request);
            configure(request);
            connection = (java.net.HttpURLConnection) new java.net.URL("https://api.openai.com/v1/chat/completions").openConnection();
            connection.setConnectTimeout(8000); connection.setReadTimeout(15000);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] body = request.toString().getBytes("UTF-8"); connection.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
            if (connection.getResponseCode() != 200) { Log.w("RadioIntent", "Intent service unavailable"); return false; }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[2048]; int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            String selected = command(new JSONObject(bytes.toString("UTF-8")));
            if (selected == null || activity.isFinishing() || !activity.hasWindowFocus() || !generationActive(activity)) return false;
            pending = new Pending(text, selected);
            Log.i("RadioIntent", "Semantic request: " + selected);
            return true;
        } catch (Exception failure) { Log.w("RadioIntent", "Intent classification unavailable"); return false; }
        finally { if (connection != null) connection.disconnect(); }
    }

    public static final String NEUTRAL_INSTRUCTION = "Decide what the current user asks for. Classify only affirmative requests to START actual radio/music as play, affirmative STOP requests as stop, and everything else as none. Describing a preference or a past event is not a request. A prohibition on starting is not a stop request. For none reply naturally in Italian.";

    private static boolean generationActive(Activity activity) {
        try {
            java.lang.reflect.Field field = activity.getClass().getDeclaredField("currentGenerationJob");
            field.setAccessible(true); Object job = field.get(activity);
            return job == null || Boolean.TRUE.equals(job.getClass().getMethod("isActive").invoke(job));
        } catch (ReflectiveOperationException ignored) { return true; }
    }
}
