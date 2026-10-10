package com.arise.system;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

/** Saves and loads the player on the device (private app storage, works offline). */
final class Storage {
    private static final String FILE = "arise_save";

    private Storage() {}

    static Player load(Context c) {
        SharedPreferences sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        Player p = new Player();
        if (!sp.contains("awakened")) return p;

        p.awakened = sp.getBoolean("awakened", false);
        p.name = sp.getString("name", "Hunter");
        p.level = sp.getInt("level", 1);
        p.xp = sp.getInt("xp", 0);
        p.points = sp.getInt("points", 0);
        for (int i = 0; i < p.stats.length; i++) {
            p.stats[i] = sp.getInt("stat" + i, 10);
        }
        p.streak = sp.getInt("streak", 0);
        p.bestStreak = sp.getInt("bestStreak", 0);
        p.lastActiveDay = sp.getInt("lastActiveDay", -1);
        p.lastAllDoneDay = sp.getInt("lastAllDoneDay", -1);
        p.totalXp = sp.getLong("totalXp", 0L);
        p.questsCompleted = sp.getInt("questsCompleted", 0);
        p.gateEndMillis = sp.getLong("gateEndMillis", 0L);
        p.gateMinutes = sp.getInt("gateMinutes", 0);

        String q = sp.getString("quests", null);
        if (q != null) {
            List<Quest> list = QuestCodec.decode(q);
            p.quests.clear();
            p.quests.addAll(list);
        }
        return p;
    }

    static void save(Context c, Player p) {
        SharedPreferences.Editor e = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit();
        e.putBoolean("awakened", p.awakened);
        e.putString("name", p.name);
        e.putInt("level", p.level);
        e.putInt("xp", p.xp);
        e.putInt("points", p.points);
        for (int i = 0; i < p.stats.length; i++) {
            e.putInt("stat" + i, p.stats[i]);
        }
        e.putInt("streak", p.streak);
        e.putInt("bestStreak", p.bestStreak);
        e.putInt("lastActiveDay", p.lastActiveDay);
        e.putInt("lastAllDoneDay", p.lastAllDoneDay);
        e.putLong("totalXp", p.totalXp);
        e.putInt("questsCompleted", p.questsCompleted);
        e.putLong("gateEndMillis", p.gateEndMillis);
        e.putInt("gateMinutes", p.gateMinutes);
        e.putString("quests", QuestCodec.encode(p.quests));
        e.apply();
    }

    static void clear(Context c) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
