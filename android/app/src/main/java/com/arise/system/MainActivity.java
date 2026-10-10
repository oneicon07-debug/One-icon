package com.arise.system;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

/**
 * ARISE SYSTEM - single-screen app: Awakening -> STATUS / QUESTS / GATE tabs.
 * The UI is built in code, uses your original images (R.drawable.arise_logo and
 * R.drawable.arise_profile) unmodified, and everything is stored locally on the device.
 */
public class MainActivity extends Activity {

    private static final int BG = 0xFF07040F;
    private static final int PANEL = 0xFF140B26;
    private static final int PANEL2 = 0xFF1F1438;
    private static final int BORDER = 0xFF3A2766;
    private static final int ACCENT = 0xFF8B3DFF;
    private static final int ACCENT2 = 0xFFC3A0FF;
    private static final int TEXT = 0xFFEDE6FF;
    private static final int DIM = 0xFF8F82B0;
    private static final int GOOD = 0xFF4DE2A0;
    private static final int WARN = 0xFFFFC857;
    private static final int DANGER = 0xFFFF6B6B;

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Player player;
    private FrameLayout content;
    private TextView[] navItems;
    private int tab = 0;
    private int selectedMinutes = 25;
    private TextView timerView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            onTick();
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(PANEL);
        player = Storage.load(this);
        if (player.awakened) {
            showMain();
        } else {
            showAwakening();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && player.awakened && content != null) {
            player.rollover(today());
            Player.Result r = player.finishGateIfDone(System.currentTimeMillis());
            Storage.save(this, player);
            selectTab(tab);
            if (r != null) {
                announce("Gate cleared!  +" + r.xp + " XP", r);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (player != null) {
            Storage.save(this, player);
        }
    }

    // ------------------------------------------------------------------ awakening screen

    private void showAwakening() {
        handler.removeCallbacks(tick);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(28), dp(40), dp(28), dp(32));
        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));

        // Your original logo, shown unmodified inside a glowing frame.
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(box(ACCENT, 0, 30));
        frame.setPadding(dp(3), dp(3), dp(3), dp(3));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.arise_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        roundCorners(logo, 27);
        frame.addView(logo, new FrameLayout.LayoutParams(dp(240), dp(240)));
        frame.setAlpha(0f);
        frame.animate().alpha(1f).setDuration(1200).start();
        col.addView(frame, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextView title = text("ARISE SYSTEM", 30, TEXT, true);
        title.setLetterSpacing(0.18f);
        title.setGravity(Gravity.CENTER);
        col.addView(title, lp(WRAP, WRAP, 28));

        TextView sub = text("SYSTEM  //  A new player has been detected.", 13, ACCENT2, false);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, lp(WRAP, WRAP, 8));

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Enter your hunter name");
        nameInput.setHintTextColor(DIM);
        nameInput.setTextColor(TEXT);
        nameInput.setSingleLine(true);
        nameInput.setGravity(Gravity.CENTER);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        nameInput.setBackground(box(PANEL, BORDER, 12));
        nameInput.setPadding(dp(16), dp(14), dp(16), dp(14));
        col.addView(nameInput, lp(MATCH, WRAP, 32));

        TextView go = button("AWAKEN", ACCENT, Color.WHITE, v -> {
            String n = nameInput.getText().toString().trim();
            if (n.isEmpty()) n = "Hunter";
            player.name = n;
            player.awakened = true;
            player.rollover(today());
            Storage.save(this, player);
            showMain();
        });
        col.addView(go, lp(MATCH, WRAP, 16));

        setContentView(sv);
    }

    // ------------------------------------------------------------------ main shell + tabs

    private void showMain() {
        player.rollover(today());
        Storage.save(this, player);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(PANEL);
        String[] names = {"STATUS", "QUESTS", "GATE"};
        navItems = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView t = text(names[i], 13, DIM, true);
            t.setGravity(Gravity.CENTER);
            t.setLetterSpacing(0.1f);
            t.setPadding(0, dp(16), 0, dp(16));
            t.setOnClickListener(v -> selectTab(idx));
            nav.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
            navItems[i] = t;
        }
        root.addView(nav, new LinearLayout.LayoutParams(MATCH, WRAP));

        setContentView(root);
        selectTab(tab);
    }

    private void selectTab(int idx) {
        tab = idx;
        handler.removeCallbacks(tick);
        timerView = null;
        for (int i = 0; i < navItems.length; i++) {
            navItems[i].setTextColor(i == idx ? ACCENT2 : DIM);
            navItems[i].setBackgroundColor(i == idx ? PANEL2 : PANEL);
        }
        content.removeAllViews();
        View v;
        if (idx == 0) {
            v = buildStatus();
        } else if (idx == 1) {
            v = buildQuests();
        } else {
            v = buildGate();
        }
        content.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    // ------------------------------------------------------------------ STATUS tab

    private View buildStatus() {
        LinearLayout col = column();
        ScrollView sv = scroller(col);

        // Header: your original player image + name / rank
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.arise_profile);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        roundCorners(avatar, 20);
        head.addView(avatar, new LinearLayout.LayoutParams(dp(104), dp(104)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), 0, 0, 0);
        info.addView(text(player.name.toUpperCase(Locale.ROOT), 22, TEXT, true));
        info.addView(text("RANK " + player.rank() + "  |  LV " + player.level, 14, ACCENT2, true), lp(WRAP, WRAP, 4));
        info.addView(text(player.title(), 13, DIM, false), lp(WRAP, WRAP, 2));
        head.addView(info, new LinearLayout.LayoutParams(0, WRAP, 1f));
        col.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Experience
        LinearLayout xpCard = card();
        xpCard.addView(text("EXPERIENCE", 12, DIM, true));
        int need = Player.xpToNext(player.level);
        float frac = player.level >= Player.MAX_LEVEL ? 1f : (float) player.xp / (float) need;
        xpCard.addView(bar(frac, ACCENT), lp(MATCH, dp(10), 10));
        String xpLabel = player.level >= Player.MAX_LEVEL
                ? "MAX LEVEL"
                : player.xp + " / " + need + " XP";
        xpCard.addView(text(xpLabel, 13, TEXT, false), lp(WRAP, WRAP, 8));
        col.addView(xpCard, lp(MATCH, WRAP, 20));

        // Abilities
        LinearLayout stats = card();
        stats.addView(text("ABILITIES", 12, DIM, true));
        if (player.points > 0) {
            stats.addView(text("Unspent points: " + player.points, 13, WARN, true), lp(WRAP, WRAP, 6));
        }
        for (int i = 0; i < Player.STAT_NAMES.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text(Player.STAT_NAMES[i], 15, TEXT, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
            row.addView(text(String.valueOf(player.stats[i]), 18, ACCENT2, true));
            if (player.points > 0) {
                TextView plus = button("+", ACCENT, Color.WHITE, v -> {
                    if (player.spendPoint(idx)) {
                        Storage.save(this, player);
                        selectTab(tab);
                    }
                });
                plus.setPadding(0, 0, 0, 0);
                LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(44), dp(38));
                pp.leftMargin = dp(14);
                row.addView(plus, pp);
            }
            stats.addView(row, lp(MATCH, WRAP, 12));
        }
        col.addView(stats, lp(MATCH, WRAP, 14));

        // Record
        LinearLayout rec = card();
        rec.addView(text("RECORD", 12, DIM, true));
        rec.addView(text("Streak: " + player.streak + " day(s)   |   Best: " + player.bestStreak, 14, TEXT, false), lp(WRAP, WRAP, 8));
        rec.addView(text("Quests completed: " + player.questsCompleted, 14, TEXT, false), lp(WRAP, WRAP, 4));
        rec.addView(text("Total XP earned: " + player.totalXp, 14, TEXT, false), lp(WRAP, WRAP, 4));
        col.addView(rec, lp(MATCH, WRAP, 14));

        TextView reset = text("RESET PROGRESS", 12, DANGER, true);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, dp(14), 0, dp(14));
        reset.setOnClickListener(v -> confirmReset());
        col.addView(reset, lp(MATCH, WRAP, 18));

        return sv;
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset progress?")
                .setMessage("This erases your level, stats and quests on this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    handler.removeCallbacks(tick);
                    Storage.clear(this);
                    player = new Player();
                    tab = 0;
                    showAwakening();
                })
                .show();
    }

    // ------------------------------------------------------------------ QUESTS tab

    private View buildQuests() {
        LinearLayout col = column();
        ScrollView sv = scroller(col);

        col.addView(text("DAILY QUESTS", 22, TEXT, true));
        col.addView(text(player.doneCount() + " / " + player.quests.size()
                + " completed   |   streak " + player.streak, 13, ACCENT2, false), lp(WRAP, WRAP, 4));
        col.addView(text("Clear every quest for a bonus. Quests reset at midnight. "
                + "Long-press a quest to delete it.", 12, DIM, false), lp(WRAP, WRAP, 4));

        for (int i = 0; i < player.quests.size(); i++) {
            final int idx = i;
            final Quest q = player.quests.get(i);

            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(q.title, 16, q.done ? DIM : TEXT, true));
            labels.addView(text("+" + q.xp + " XP", 12, q.done ? DIM : WARN, false), lp(WRAP, WRAP, 2));
            row.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1f));

            TextView act;
            if (q.done) {
                act = button("DONE", 0xFF1F3B30, GOOD, null);
            } else {
                act = button("COMPLETE", ACCENT, Color.WHITE, v -> onCompleteQuest(idx));
            }
            act.setTextSize(12);
            act.setLetterSpacing(0.04f);
            act.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(WRAP, WRAP);
            ap.leftMargin = dp(10);
            row.addView(act, ap);

            row.setOnLongClickListener(v -> {
                confirmDeleteQuest(idx);
                return true;
            });
            col.addView(row, lp(MATCH, WRAP, 10));
        }

        col.addView(button("+  ADD QUEST", PANEL2, ACCENT2, v -> showAddQuestDialog()), lp(MATCH, WRAP, 16));
        return sv;
    }

    private void onCompleteQuest(int idx) {
        Player.Result r = player.completeQuest(idx, today());
        if (r.xp == 0) return;
        Storage.save(this, player);
        String msg = "Quest complete!  +" + r.xp + " XP";
        if (r.allDone) {
            msg += "\nAll quests cleared!  Bonus +" + r.bonusXp + " XP  |  streak " + player.streak;
        }
        selectTab(tab);
        announce(msg, r);
    }

    private void confirmDeleteQuest(final int idx) {
        if (idx < 0 || idx >= player.quests.size()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete quest?")
                .setMessage(player.quests.get(idx).title)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    player.deleteQuest(idx);
                    Storage.save(this, player);
                    selectTab(tab);
                })
                .show();
    }

    private void showAddQuestDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);

        final EditText title = new EditText(this);
        title.setHint("Quest (e.g. Meditate 10 min)");
        title.setSingleLine(true);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Player.MAX_TITLE)});
        form.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        final EditText xp = new EditText(this);
        xp.setHint("XP reward (" + Player.MIN_XP + "-" + Player.MAX_XP + ")");
        xp.setInputType(InputType.TYPE_CLASS_NUMBER);
        xp.setText("30");
        form.addView(xp, new LinearLayout.LayoutParams(MATCH, WRAP));

        new AlertDialog.Builder(this)
                .setTitle("New quest")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    int reward = 30;
                    try {
                        reward = Integer.parseInt(xp.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        // keep the default
                    }
                    if (player.addQuest(title.getText().toString(), reward)) {
                        Storage.save(this, player);
                        selectTab(tab);
                    } else {
                        Toast.makeText(this, "Enter a quest name (max " + Player.MAX_QUESTS + " quests).",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ GATE tab (focus timer)

    private View buildGate() {
        LinearLayout col = column();
        ScrollView sv = scroller(col);

        col.addView(text("DUNGEON GATE", 22, TEXT, true));
        col.addView(text("Open a gate to focus. Let the timer finish to clear it and earn 2 XP per minute. "
                + "The timer keeps running if you leave the app.", 12, DIM, false), lp(WRAP, WRAP, 4));

        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        if (player.gateActive()) {
            card.addView(text("GATE OPEN", 12, WARN, true));
            timerView = text("00:00", 56, TEXT, true);
            timerView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            card.addView(timerView, lp(WRAP, WRAP, 10));
            card.addView(text("Reward: +" + Player.gateXp(player.gateMinutes) + " XP", 14, ACCENT2, true), lp(WRAP, WRAP, 6));
            card.addView(button("ABANDON GATE", PANEL2, DANGER, v -> {
                player.abandonGate();
                Storage.save(this, player);
                selectTab(tab);
            }), lp(MATCH, WRAP, 20));
            updateTimerText(System.currentTimeMillis());
            handler.postDelayed(tick, 500);
        } else {
            card.addView(text("CHOOSE DURATION", 12, DIM, true));
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            int[] options = {15, 25, 45, 60};
            for (final int m : options) {
                boolean sel = (m == selectedMinutes);
                TextView chip = button(m + " MIN", sel ? ACCENT : PANEL2, sel ? Color.WHITE : ACCENT2, v -> {
                    selectedMinutes = m;
                    selectTab(tab);
                });
                chip.setTextSize(12);
                chip.setLetterSpacing(0f);
                chip.setPadding(dp(2), dp(12), dp(2), dp(12));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
                cp.leftMargin = dp(4);
                cp.rightMargin = dp(4);
                chips.addView(chip, cp);
            }
            card.addView(chips, lp(MATCH, WRAP, 12));
            card.addView(text("Reward: +" + Player.gateXp(selectedMinutes) + " XP", 14, ACCENT2, true), lp(WRAP, WRAP, 16));
            card.addView(button("ENTER GATE", ACCENT, Color.WHITE, v -> {
                player.startGate(selectedMinutes, System.currentTimeMillis());
                Storage.save(this, player);
                selectTab(tab);
            }), lp(MATCH, WRAP, 16));
        }
        col.addView(card, lp(MATCH, WRAP, 16));
        return sv;
    }

    private void onTick() {
        if (player == null || !player.gateActive()) return;
        long now = System.currentTimeMillis();
        Player.Result r = player.finishGateIfDone(now);
        if (r != null) {
            Storage.save(this, player);
            selectTab(tab);
            announce("Gate cleared!  +" + r.xp + " XP", r);
            return;
        }
        updateTimerText(now);
        handler.postDelayed(tick, 500);
    }

    private void updateTimerText(long now) {
        if (timerView == null) return;
        long secs = (player.gateRemainingMillis(now) + 999L) / 1000L;
        timerView.setText(String.format(Locale.US, "%02d:%02d", secs / 60L, secs % 60L));
    }

    // ------------------------------------------------------------------ helpers

    private void announce(String msg, Player.Result r) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        if (r.levelsGained > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("LEVEL UP!")
                    .setMessage("You are now level " + player.level + "  (Rank " + player.rank() + ").\n"
                            + "+" + (r.levelsGained * Player.POINTS_PER_LEVEL)
                            + " ability points. Spend them on the STATUS tab.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    /** Days since 1970 in the phone's local time zone. */
    private int today() {
        Calendar c = Calendar.getInstance();
        long local = c.getTimeInMillis() + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET);
        return (int) (local / 86_400_000L);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable box(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String label, int fill, int textColor, View.OnClickListener l) {
        TextView t = text(label, 14, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(20), dp(14), dp(20), dp(14));
        t.setBackground(box(fill, 0, 12));
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(PANEL, BORDER, 16));
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        return c;
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(24), dp(20), dp(24));
        return col;
    }

    private ScrollView scroller(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        return sv;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    private View bar(float frac, int color) {
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(box(0xFF2A1F45, 0, 6));
        int f = Math.round(Math.max(0f, Math.min(1f, frac)) * 1000f);
        View fill = new View(this);
        fill.setBackground(box(color, 0, 6));
        track.addView(fill, new LinearLayout.LayoutParams(0, MATCH, f));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, MATCH, 1000 - f));
        return track;
    }

    /** Rounds the corners of a view's on-screen shape only; the image file itself is never changed. */
    private void roundCorners(View v, int radiusDp) {
        final float r = dp(radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }
}
