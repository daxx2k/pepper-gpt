package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.object.conversation.Say;

public final class WeatherNarration {
    public static void speak(Activity activity, Future<Say> pending) {
        pending.thenConsume(result -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (result.isCancelled() || result.hasError()) {
                SpecialScreen.closeAfterSpeech(activity);
                return;
            }
            Future<Void> speech = LegacyVoiceAdapter.runAsync(LegacyVoiceAdapter.async(result.get()));
            speech.thenConsume(done -> SpecialScreen.closeAfterSpeech(activity));
        });
    }
}
