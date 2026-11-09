package com.softbankrobotics.pepper.pepperGPT;

import org.json.JSONObject;

/** Converts a structured OpenAI billing error into a message safe to show and speak. */
final class RealtimeBilling {
    private RealtimeBilling() {}

    static String notice(JSONObject error, boolean italian) {
        if (error == null) return null;
        String code = error.optString("code", "");
        switch (code) {
            case "credit_balance_exhausted":
                return italian ? "I crediti API di OpenAI sono esauriti. Ricaricali per continuare a parlare con me."
                        : "Your OpenAI API credits have run out. Top them up to keep talking with me.";
            case "organization_spend_limit_exceeded":
            case "project_spend_limit_exceeded":
            case "billing_hard_limit_reached":
                return italian ? "È stato raggiunto un limite di spesa dell'API OpenAI. Controlla le impostazioni di fatturazione."
                        : "An OpenAI API spending limit has been reached. Check your billing settings.";
            case "organization_usage_limit_exceeded":
                return italian ? "È stato raggiunto un limite di utilizzo dell'API OpenAI. Controlla i limiti del tuo account."
                        : "An OpenAI API usage limit has been reached. Check your account limits.";
            case "insufficient_quota":
                return quotaNotice(italian);
            case "":
                return "insufficient_quota".equals(error.optString("type")) ? quotaNotice(italian) : null;
            default:
                return null;
        }
    }

    private static String quotaNotice(boolean italian) {
        return italian ? "La quota API di OpenAI è esaurita. Controlla il saldo e i limiti. Se i crediti sono finiti, ricaricali."
                : "Your OpenAI API quota is exhausted. Check your balance and limits. If you are out of credits, top them up.";
    }
}
