package com.dopaminequest.models;

import java.util.ArrayList;
import java.util.List;

public class Task {

    public enum VerificationType { TIMER, PHOTO }

    public final int              id;
    public final String           emoji;
    public final String           title;
    public final String           tag;
    public final int              xp;
    public final int              durationMins;
    public final VerificationType verification;
    public final int              accessMinutes;

    public Task(int id, String emoji, String title, String tag,
                int xp, int durationMins, VerificationType v) {
        this.id           = id;
        this.emoji        = emoji;
        this.title        = title;
        this.tag          = tag;
        this.xp           = xp;
        this.durationMins = durationMins;
        this.verification = v;
        this.accessMinutes = calcAccess(durationMins, v);
    }

    private static int calcAccess(int mins, VerificationType v) {
        if (v == VerificationType.PHOTO) return mins >= 4 ? 30 : 25;
        switch (mins) { case 1: return 10; case 2: return 15; case 3: return 20; default: return 25; }
    }

    public static List<Task> all() {
        List<Task> t = new ArrayList<>();
        t.add(new Task(1,  "💧", "Drink a full glass of water",        "Reset", 10, 1, VerificationType.TIMER));
        t.add(new Task(2,  "🧹", "Clear your desk surface",             "Focus", 15, 2, VerificationType.PHOTO));
        t.add(new Task(3,  "🌬", "Take 5 slow deep breaths",            "Reset", 10, 1, VerificationType.TIMER));
        t.add(new Task(4,  "📝", "Write your top 3 goals for today",    "Focus", 25, 4, VerificationType.PHOTO));
        t.add(new Task(5,  "💪", "Do 5 slow push-ups",                  "Move",  20, 2, VerificationType.TIMER));
        t.add(new Task(6,  "☀", "Step outside for fresh air",          "Reset", 18, 3, VerificationType.PHOTO));
        t.add(new Task(7,  "🗂", "Close all unused tabs",               "Focus", 12, 1, VerificationType.TIMER));
        t.add(new Task(8,  "🎯", "Define your ONE goal today",          "Focus", 30, 4, VerificationType.PHOTO));
        t.add(new Task(9,  "🧘", "Stand up and stretch",                "Move",  12, 1, VerificationType.TIMER));
        t.add(new Task(10, "📵", "Put phone face-down for 10 minutes",  "Reset", 20, 2, VerificationType.TIMER));
        return t;
    }

    public static Task findById(int id) {
        for (Task t : all()) if (t.id == id) return t;
        return null;
    }
}
