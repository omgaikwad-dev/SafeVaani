package com.example.woshield;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Routine implements Serializable {
    private String id;
    private String userId;
    private String title;
    private String category;
    private String startLocation;
    private String destination;
    private String startTime;
    private String arrivalTime;
    private int startHour;
    private int startMinute;
    private int arrivalHour;
    private int arrivalMinute;
    private List<String> daysOfWeek;
    private int expectedDurationMinutes;
    private boolean enabled;
    private List<String> safeStopIds;
    private List<String> safeStopNames;
    private boolean checkInEnabled;
    private int checkInIntervalMinutes;
    private boolean reminderNotificationEnabled;
    private int reminderMinutesBefore;
    private List<String> trustedContactNumbers;
    private long createdAt;
    private long updatedAt;

    // Required default constructor for Firebase
    public Routine() {
        this.daysOfWeek = new ArrayList<>();
        this.safeStopIds = new ArrayList<>();
        this.safeStopNames = new ArrayList<>();
        this.trustedContactNumbers = new ArrayList<>();
        this.enabled = true;
    }

    public Routine(String id, String userId, String title, String category,
                   String startLocation, String destination,
                   String startTime, String arrivalTime,
                   int startHour, int startMinute, int arrivalHour, int arrivalMinute,
                   List<String> daysOfWeek, int expectedDurationMinutes, boolean enabled) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.category = category;
        this.startLocation = startLocation;
        this.destination = destination;
        this.startTime = startTime;
        this.arrivalTime = arrivalTime;
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.arrivalHour = arrivalHour;
        this.arrivalMinute = arrivalMinute;
        this.daysOfWeek = daysOfWeek != null ? daysOfWeek : new ArrayList<>();
        this.expectedDurationMinutes = expectedDurationMinutes;
        this.enabled = enabled;
        this.safeStopIds = new ArrayList<>();
        this.safeStopNames = new ArrayList<>();
        this.trustedContactNumbers = new ArrayList<>();
        this.checkInEnabled = false;
        this.checkInIntervalMinutes = 15;
        this.reminderNotificationEnabled = true;
        this.reminderMinutesBefore = 10;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStartLocation() { return startLocation; }
    public void setStartLocation(String startLocation) { this.startLocation = startLocation; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }

    public int getArrivalHour() { return arrivalHour; }
    public void setArrivalHour(int arrivalHour) { this.arrivalHour = arrivalHour; }

    public int getArrivalMinute() { return arrivalMinute; }
    public void setArrivalMinute(int arrivalMinute) { this.arrivalMinute = arrivalMinute; }

    public List<String> getDaysOfWeek() {
        return daysOfWeek != null ? daysOfWeek : new ArrayList<>();
    }
    public void setDaysOfWeek(List<String> daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public int getExpectedDurationMinutes() { return expectedDurationMinutes; }
    public void setExpectedDurationMinutes(int expectedDurationMinutes) { this.expectedDurationMinutes = expectedDurationMinutes; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getSafeStopIds() {
        return safeStopIds != null ? safeStopIds : new ArrayList<>();
    }
    public void setSafeStopIds(List<String> safeStopIds) { this.safeStopIds = safeStopIds; }

    public List<String> getSafeStopNames() {
        return safeStopNames != null ? safeStopNames : new ArrayList<>();
    }
    public void setSafeStopNames(List<String> safeStopNames) { this.safeStopNames = safeStopNames; }

    public boolean isCheckInEnabled() { return checkInEnabled; }
    public void setCheckInEnabled(boolean checkInEnabled) { this.checkInEnabled = checkInEnabled; }

    public int getCheckInIntervalMinutes() { return checkInIntervalMinutes; }
    public void setCheckInIntervalMinutes(int checkInIntervalMinutes) { this.checkInIntervalMinutes = checkInIntervalMinutes; }

    public boolean isReminderNotificationEnabled() { return reminderNotificationEnabled; }
    public void setReminderNotificationEnabled(boolean reminderNotificationEnabled) { this.reminderNotificationEnabled = reminderNotificationEnabled; }

    public int getReminderMinutesBefore() { return reminderMinutesBefore; }
    public void setReminderMinutesBefore(int reminderMinutesBefore) { this.reminderMinutesBefore = reminderMinutesBefore; }

    public List<String> getTrustedContactNumbers() {
        return trustedContactNumbers != null ? trustedContactNumbers : new ArrayList<>();
    }
    public void setTrustedContactNumbers(List<String> trustedContactNumbers) { this.trustedContactNumbers = trustedContactNumbers; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getFormattedDays() {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return "No days scheduled";
        }
        if (daysOfWeek.size() == 7) {
            return "Every day";
        }
        if (daysOfWeek.size() == 5 &&
                daysOfWeek.contains("Mon") && daysOfWeek.contains("Tue") &&
                daysOfWeek.contains("Wed") && daysOfWeek.contains("Thu") &&
                daysOfWeek.contains("Fri")) {
            return "Weekdays (Mon - Fri)";
        }
        if (daysOfWeek.size() == 2 &&
                daysOfWeek.contains("Sat") && daysOfWeek.contains("Sun")) {
            return "Weekends (Sat, Sun)";
        }
        return String.join(", ", daysOfWeek);
    }
}
