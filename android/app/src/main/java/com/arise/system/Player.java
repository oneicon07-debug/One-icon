package com.arise.system;

import java.util.ArrayList;
import java.util.List;

/**
 * All game state and rules. Pure Java (no Android dependencies) so the logic can be tested anywhere.
 * Days are passed in as "epoch day" integers so the logic never touches the clock itself.
 */
public class Player {
    public static final int MAX_LEVEL = 100;
    public static final int POINTS_PER_LEVEL = 3;
    public static final int MAX_QUESTS = 12;
    public static final int MAX_TITLE = 40;
    public static final int MIN_XP = 5;
    public static final int MAX_XP = 200;

    public static final String[] STAT_NAMES = {"STRENGTH", "AGILITY", "VITALITY", "INTELLIGENCE", "SENSE"};
    private static final String[] RANKS = {"E", "D", "C", "B", "A", "S"};
    private static final String[] TITLES = {
            "Novice Hunter", "Apprentice Hunter", "Veteran Hunter",
            "Elite Hunter", "Master Hunter", "S-Rank Legend"};

    /** Outcome of an action that awards XP. */
    public static class Result {
        public int xp;
        public int bonusXp;
        public int levelsGained;
        public boolean allDone;
    }

    public String name = "Hunter";
    public boolean awakened = false;
    public int level = 1;
    public int xp = 0;
    public int points = 0;
    public int[] stats = {10, 10, 10, 10, 10};
    public int streak = 0;
    public int bestStreak = 0;
    public int lastActiveDay = -1;
    public int lastAllDoneDay = -1;
    public long totalXp = 0;
    public int questsCompleted = 0;
    public long gateEndMillis = 0;
    public int gateMinutes = 0;
    public final List<Quest> quests = new ArrayList<>();

    public Player() {
        quests.addAll(Quest.defaults());
    }

    // ---------- levels ----------

    public static int xpToNext(int level) {
        int n = level - 1;
        return 100 + 30 * n + 2 * n * n;
    }

    /** Adds XP and returns how many levels were gained. */
    public int addXp(int amount) {
        if (amount <= 0) return 0;
        totalXp += amount;
        if (level >= MAX_LEVEL) return 0;
        xp += amount;
        int gained = 0;
        while (level < MAX_LEVEL && xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            points += POINTS_PER_LEVEL;
            gained++;
        }
        if (level >= MAX_LEVEL) xp = 0;
        return gained;
    }

    public static int rankIndex(int level) {
        if (level < 10) return 0;
        if (level < 20) return 1;
        if (level < 35) return 2;
        if (level < 50) return 3;
        if (level < 70) return 4;
        return 5;
    }

    public String rank() {
        return RANKS[rankIndex(level)];
    }

    public String title() {
        return TITLES[rankIndex(level)];
    }

    public boolean spendPoint(int stat) {
        if (points <= 0 || stat < 0 || stat >= stats.length) return false;
        stats[stat]++;
        points--;
        return true;
    }

    // ---------- daily quests ----------

    /** Call when the app opens or resumes. Resets quests on a new day and breaks a missed streak. */
    public boolean rollover(int today) {
        if (lastActiveDay == today) return false;
        for (Quest q : quests) q.done = false;
        if (lastAllDoneDay != today - 1 && lastAllDoneDay != today) streak = 0;
        lastActiveDay = today;
        return true;
    }

    public int doneCount() {
        int n = 0;
        for (Quest q : quests) if (q.done) n++;
        return n;
    }

    public boolean allQuestsDone() {
        return !quests.isEmpty() && doneCount() == quests.size();
    }

    public Result completeQuest(int index, int today) {
        Result r = new Result();
        if (index < 0 || index >= quests.size()) return r;
        Quest q = quests.get(index);
        if (q.done) return r;
        q.done = true;
        questsCompleted++;
        r.xp = q.xp;
        r.levelsGained += addXp(q.xp);
        if (allQuestsDone() && lastAllDoneDay != today) {
            streak = (lastAllDoneDay == today - 1) ? streak + 1 : 1;
            if (streak > bestStreak) bestStreak = streak;
            lastAllDoneDay = today;
            r.allDone = true;
            r.bonusXp = 50 + 10 * Math.min(streak, 20);
            r.levelsGained += addXp(r.bonusXp);
        }
        return r;
    }

    public static int clampXp(int xp) {
        return Math.max(MIN_XP, Math.min(MAX_XP, xp));
    }

    public boolean addQuest(String title, int xp) {
        if (title == null) return false;
        String t = title.trim();
        if (t.isEmpty() || quests.size() >= MAX_QUESTS) return false;
        if (t.length() > MAX_TITLE) t = t.substring(0, MAX_TITLE);
        quests.add(new Quest(t, clampXp(xp)));
        return true;
    }

    public boolean deleteQuest(int index) {
        if (index < 0 || index >= quests.size()) return false;
        quests.remove(index);
        return true;
    }

    // ---------- dungeon gate (focus timer) ----------

    public static int gateXp(int minutes) {
        return minutes * 2;
    }

    public boolean gateActive() {
        return gateEndMillis > 0;
    }

    public void startGate(int minutes, long nowMillis) {
        gateMinutes = minutes;
        gateEndMillis = nowMillis + minutes * 60_000L;
    }

    public void abandonGate() {
        gateEndMillis = 0;
        gateMinutes = 0;
    }

    public long gateRemainingMillis(long nowMillis) {
        return Math.max(0L, gateEndMillis - nowMillis);
    }

    /** If a focus session has finished, awards its XP, clears it and returns the result; otherwise null. */
    public Result finishGateIfDone(long nowMillis) {
        if (gateEndMillis <= 0 || nowMillis < gateEndMillis) return null;
        Result r = new Result();
        r.xp = gateXp(gateMinutes);
        r.levelsGained = addXp(r.xp);
        abandonGate();
        return r;
    }
}
