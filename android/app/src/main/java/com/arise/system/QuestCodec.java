package com.arise.system;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves the quest list as one plain string: title:xp:done,title:xp:done
 * Titles are URL-encoded so they can never contain the separators.
 */
public final class QuestCodec {
    private QuestCodec() {}

    public static String encode(List<Quest> quests) {
        StringBuilder sb = new StringBuilder();
        for (Quest q : quests) {
            if (sb.length() > 0) sb.append(',');
            sb.append(enc(q.title)).append(':').append(q.xp).append(':').append(q.done ? 1 : 0);
        }
        return sb.toString();
    }

    public static List<Quest> decode(String s) {
        List<Quest> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) {
            String[] f = part.split(":");
            if (f.length != 3) continue;
            try {
                Quest q = new Quest(dec(f[0]), Player.clampXp(Integer.parseInt(f[1])));
                q.done = "1".equals(f[2]);
                out.add(q);
            } catch (IllegalArgumentException e) {
                // skip a damaged entry rather than crash
            }
        }
        return out;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
