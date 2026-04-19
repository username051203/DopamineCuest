package com.dopaminequest.models;

import java.util.ArrayList;
import java.util.List;

public class Task {

    public enum VerificationType { TIMER, PHOTO, STUDY }

    public final int              id;
    public final String           emoji;
    public final String           title;
    public final String           tag;
    public final boolean          isPhysical;
    public final int              xp;
    public final int              durationMins;
    public final VerificationType verification;
    public final int              accessMinutes;

    public Task(int id, String emoji, String title, String tag,
                boolean isPhysical, int xp, int durationMins, VerificationType v) {
        this.id           = id;
        this.emoji        = emoji;
        this.title        = title;
        this.tag          = tag;
        this.isPhysical   = isPhysical;
        this.xp           = xp;
        this.durationMins = durationMins;
        this.verification = v;
        this.accessMinutes = calcAccess(durationMins, v, isPhysical);
    }

    private static int calcAccess(int mins, VerificationType v, boolean physical) {
        if (v == VerificationType.STUDY) return 25;
        if (physical) return 15; // physical tasks always 15 min regardless of duration
        if (v == VerificationType.PHOTO) return mins >= 4 ? 12 : 10;
        switch (mins) {
            case 1:  return 5;
            case 2:  return 8;
            case 3:  return 10;
            default: return 12;
        }
    }

    public static List<Task> all() {
        List<Task> t = new ArrayList<>();
        t.add(new Task(1,  "💧", "Drink a full glass of water",        "Reset", false, 10, 1, VerificationType.TIMER));
        t.add(new Task(2,  "🧹", "Clear your desk surface",             "Focus", false, 15, 2, VerificationType.PHOTO));
        t.add(new Task(3,  "🌬", "Take 5 slow deep breaths",            "Reset", false, 10, 1, VerificationType.TIMER));
        t.add(new Task(4,  "📝", "Write your top 3 goals for today",    "Focus", false, 25, 4, VerificationType.PHOTO));
        t.add(new Task(5,  "💪", "Do 10 push-ups",                      "Move",  true,  20, 2, VerificationType.TIMER));
        t.add(new Task(6,  "☀", "Step outside for fresh air",          "Reset", false, 18, 3, VerificationType.PHOTO));
        t.add(new Task(7,  "🗂", "Close all unused tabs",               "Focus", false, 12, 1, VerificationType.TIMER));
        t.add(new Task(8,  "🎯", "Define your ONE goal today",          "Focus", false, 30, 4, VerificationType.PHOTO));
        t.add(new Task(9,  "🧘", "10-minute stretch routine",           "Move",  true,  20, 10, VerificationType.TIMER));
        t.add(new Task(10, "🏃", "20-minute walk or run",               "Move",  true,  30, 20, VerificationType.TIMER));
        t.add(new Task(11, "📚", "Study session — 6 pages",             "Focus", false, 50, 30, VerificationType.STUDY));
        return t;
    }

    public static Task findById(int id) {
        for (Task t : all()) if (t.id == id) return t;
        return null;
    }
}
