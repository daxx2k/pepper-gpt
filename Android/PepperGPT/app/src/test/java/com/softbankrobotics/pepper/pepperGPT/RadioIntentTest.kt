package com.softbankrobotics.pepper.pepperGPT

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RadioIntentTest {
    @Test fun ambiguousAndNegatedMentionsUseSemanticUnderstanding() {
        assertTrue(RadioIntent.fastCommand("play radio"))
        assertTrue(RadioIntent.fastCommand("Stop music!"))
        assertFalse(RadioIntent.fastCommand("do not play radio"))
        assertFalse(RadioIntent.fastCommand("non play radio"))
        assertFalse(RadioIntent.fastCommand("Tell me about synthwave"))
        assertFalse(RadioIntent.fastCommand("Ehi Pepper, mi fai sentire la radio?"))
    }
    private fun response(action: String, station: String, name: String = "pepper_reply"): JSONObject =
        JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject()
            .put("tool_calls", JSONArray().put(JSONObject().put("function", JSONObject()
                .put("name", name).put("arguments", JSONObject().put("action", action)
                    .put("station", station).put("reply", "").toString())))))))

    @Test fun validatedActionsUseOriginalRadioCommands() {
        assertEquals("play radio", RadioIntent.command(response("play", "default")))
        assertEquals("play radio pop", RadioIntent.command(response("play", "pop")))
        assertEquals("play radio synthwave", RadioIntent.command(response("play", "synthwave")))
        assertEquals("stop radio", RadioIntent.command(response("stop", "default")))
    }

    @Test fun unrelatedOrInvalidToolCallsCannotStartPlayback() {
        assertNull(RadioIntent.command(response("play", "https://untrusted.example/audio")))
        assertNull(RadioIntent.command(response("dance", "default")))
        assertNull(RadioIntent.command(response("play", "default", "other_function")))
        assertNull(RadioIntent.command(JSONObject()))
        assertNull(RadioIntent.command(JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("message", JSONObject().put("content", "We were talking about radio."))))))
    }

    @Test fun conversationMayAnswerWithoutCallingARadioTool() {
        val body = JSONObject().put("model", "unchanged-model").put("messages", JSONArray())
        RadioIntent.configure(body)
        assertEquals("unchanged-model", body.getString("model"))
        assertEquals("required", body.getString("tool_choice"))
        assertFalse(body.getBoolean("parallel_tool_calls"))
        val function = body.getJSONArray("tools").getJSONObject(0).getJSONObject("function")
        assertEquals("pepper_reply", function.getString("name"))
        assertTrue(function.getBoolean("strict"))
        assertFalse(function.getJSONObject("parameters").getBoolean("additionalProperties"))
    }
}
