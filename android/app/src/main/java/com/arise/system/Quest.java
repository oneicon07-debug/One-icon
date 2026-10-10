package com.arise.system;

import java.util.ArrayList;
import java.util.List;

/** One daily quest. Pure Java (no Android dependencies). */
public class Quest {
    public String title;
    public int xp;
    public boolean done;

    public Quest(String title, int xp) {
        this.title = title;
        this.xp = xp;
        this.done = false;
    }

    public static List<Quest> defaults() {
        List<Quest> list = new ArrayList<>();
        list.add(new Quest("Push-ups x50", 30));
        list.add(new Quest("Sit-ups x50", 30));
        list.add(new Quest("Squats x50", 30));
        list.add(new Quest("Run 3 km", 50));
        list.add(new Quest("Read or study 30 min", 30));
        return list;
    }
}
