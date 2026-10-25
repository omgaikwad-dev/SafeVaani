package com.example.woshield;

import java.util.HashMap;
import java.util.Map;

public class SurakshaVoiceAI {

    private final Map<String, String> threatKeywords = new HashMap<>();

    public SurakshaVoiceAI() {
        // Tier 1: Immediate Danger & Distress (Highest Priority)
        threatKeywords.put("help", "EMERGENCY");
        threatKeywords.put("save me", "EMERGENCY");
        threatKeywords.put("danger", "EMERGENCY");
        threatKeywords.put("emergency", "EMERGENCY");
        threatKeywords.put("i am in trouble", "EMERGENCY");
        threatKeywords.put("call the police", "EMERGENCY");
        threatKeywords.put("get away from me", "THREAT");
        threatKeywords.put("leave me alone", "THREAT");
        threatKeywords.put("stop following me", "THREAT");

        // Tier 2: Physical & Verbal Assault
        threatKeywords.put("don't touch me", "ASSAULT");
        threatKeywords.put("get your hands off me", "ASSAULT");
        threatKeywords.put("he's attacking me", "ASSAULT");
        threatKeywords.put("she's hitting me", "ASSAULT");
        threatKeywords.put("i will hurt you", "VIOLENCE");
        threatKeywords.put("i will kill you", "VIOLENCE");

        // Tier 3: Kidnapping & Abduction
        threatKeywords.put("kidnap", "ABDUCTION");
        threatKeywords.put("abduction", "ABDUCTION");
        threatKeywords.put("let me go", "ABDUCTION");
        threatKeywords.put("get in the car", "ABDUCTION");

        // Tier 4: Domestic Abuse
        threatKeywords.put("he is abusing me", "DOMESTIC_ABUSE");
        threatKeywords.put("she is hurting me", "DOMESTIC_ABUSE");
    }

    public String getThreatCategory(String text) {
        String lowerText = text.toLowerCase();
        for (Map.Entry<String, String> entry : threatKeywords.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null; // No threat detected
    }

    /**
     * Returns the specific keyword that matched the threat.
     */
    public String getMatchingKeyword(String text) {
        String lowerText = text.toLowerCase();
        for (String keyword : threatKeywords.keySet()) {
            if (lowerText.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }
}
