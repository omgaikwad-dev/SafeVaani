package com.example.woshield;

/**
 * RoadMapStep — Data model for a turn-by-turn road navigation step
 * in SafeVaani's NightShift Road Map.
 */
public class RoadMapStep {
    public String stepSeq;
    public String distanceText;
    public String roadName;
    public String instruction;
    public String safetyBadge;
    public String iconEmoji;
    public double latitude;
    public double longitude;
    public boolean isFirst;
    public boolean isLast;
    public int badgeColorRes;
    public int badgeBgColorRes;
    public int roadLineColorRes;

    public RoadMapStep(String stepSeq, String distanceText, String roadName,
                       String instruction, String safetyBadge, String iconEmoji,
                       double latitude, double longitude,
                       int badgeColorRes, int badgeBgColorRes, int roadLineColorRes) {
        this.stepSeq = stepSeq;
        this.distanceText = distanceText;
        this.roadName = roadName;
        this.instruction = instruction;
        this.safetyBadge = safetyBadge;
        this.iconEmoji = iconEmoji;
        this.latitude = latitude;
        this.longitude = longitude;
        this.badgeColorRes = badgeColorRes;
        this.badgeBgColorRes = badgeBgColorRes;
        this.roadLineColorRes = roadLineColorRes;
        this.isFirst = false;
        this.isLast = false;
    }
}
