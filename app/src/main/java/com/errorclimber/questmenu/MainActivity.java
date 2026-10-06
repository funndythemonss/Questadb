package com.errorclimber.questmenu;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS = "errorclimber_state";
    private static final int BG = Color.rgb(7, 7, 12);
    private static final int PANEL = Color.rgb(19, 18, 28);
    private static final int PURPLE = Color.rgb(138, 92, 255);
    private static final int TEXT = Color.rgb(245, 243, 255);
    private static final int MUTED = Color.rgb(166, 162, 185);

    private final Map<String, Boolean> featureState = new LinkedHashMap<>();
    private final Map<String, TextView> statusViews = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout panel;
    private TextView hint;
    private TextView diagnostic;
    private boolean menuOpen = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(Window.FEATURE_NO_TITLE, Window.FEATURE_NO_TITLE);
        getWindow().setFlags(1024, 1024);
        enterImmersive();
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        featureState.put("Platforms", prefs.getBoolean("Platforms", false));
        featureState.put("Fly", prefs.getBoolean("Fly", false));
        featureState.put("Super Jump", prefs.getBoolean("Super Jump", false));
        featureState.put("Long Arms", prefs.getBoolean("Long Arms", false));

        buildUi();
        runFeatureSelfTest();
        renderMenu();
    }

    private void enterImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private GradientDrawable rounded(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private TextView label(String text, float size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        return v;
    }

    private Button actionButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(20);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans", Typeface.BOLD));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(18, 12, 18, 12);
        b.setBackground(rounded(Color.rgb(34, 32, 47), 28, Color.rgb(70, 66, 90), 2));
        return b;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(30, 26, 30, 26);
        panel.setBackground(rounded(PANEL, 36, Color.rgb(64, 58, 88), 3));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                860, 0);
        panelParams.height = dp(670);
        panelParams.gravity = android.view.Gravity.CENTER;
        panelParams.leftMargin = dp(24);
        panelParams.rightMargin = dp(24);
        root.addView(panel, panelParams);

        TextView title = label("ERRORCLIMBER", 30, PURPLE);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView subtitle = label("Quest Menu  •  Y toggles menu", 17, MUTED);
        subtitle.setPadding(0, 0, 0, dp(14));
        panel.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(42)));

        diagnostic = label("Self-test: checking…", 16, MUTED);
        panel.addView(diagnostic, new LinearLayout.LayoutParams(-1, dp(38)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        addFeatureRow(grid, "Platforms", "Platform toggle");
        addFeatureRow(grid, "Fly", "Fly toggle");
        addFeatureRow(grid, "Super Jump", "Jump boost toggle");
        addFeatureRow(grid, "Long Arms", "Arm length toggle");

        scroll.addView(grid);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Space space = new Space(this);
        panel.addView(space, new LinearLayout.LayoutParams(1, dp(8)));

        Button reset = actionButton("RESET ALL");
        reset.setBackground(rounded(Color.rgb(56, 25, 35), 26, Color.rgb(110, 58, 75), 2));
        reset.setOnClickListener(v -> resetAll());
        panel.addView(reset, new LinearLayout.LayoutParams(-1, dp(72)));

        hint = label("Press Y again to close.  Use the controller pointer to select a button.", 15, MUTED);
        hint.setPadding(0, dp(10), 0, 0);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView footer = label("Standalone panel • no ADB • no network permission", 13, MUTED);
        footer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        panel.addView(footer, new LinearLayout.LayoutParams(-1, dp(26)));

        setContentView(root);
    }

    private void addFeatureRow(LinearLayout parent, final String name, String description) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(12), dp(18), dp(12));
        row.setBackground(rounded(Color.rgb(26, 24, 37), 26, Color.rgb(55, 51, 72), 2));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = label(name, 21, TEXT);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        TextView desc = label(description, 14, MUTED);

        textBox.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));
        textBox.addView(desc, new LinearLayout.LayoutParams(-1, dp(26)));
        row.addView(textBox, new LinearLayout.LayoutParams(0, dp(76), 1f));

        TextView state = label("OFF", 18, Color.rgb(230, 220, 230));
        state.setGravity(android.view.Gravity.CENTER);
        state.setTypeface(Typeface.create("sans", Typeface.BOLD));
        statusViews.put(name, state);
        row.addView(state, new LinearLayout.LayoutParams(dp(92), dp(58)));

        Button toggle = actionButton("TOGGLE");
        toggle.setTextSize(16);
        toggle.setOnClickListener(v -> {
            boolean next = !featureState.get(name);
            featureState.put(name, next);
            prefs.edit().putBoolean(name, next).apply();
            renderMenu();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(dp(150), dp(76)));

        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(100)));
        Space s = new Space(this);
        parent.addView(s, new LinearLayout.LayoutParams(1, dp(10)));
    }

    private void renderMenu() {
        if (!menuOpen) {
            panel.setVisibility(View.GONE);
        } else {
            panel.setVisibility(View.VISIBLE);
        }
        for (Map.Entry<String, Boolean> e : featureState.entrySet()) {
            TextView state = statusViews.get(e.getKey());
            if (state == null) continue;
            boolean on = e.getValue();
            state.setText(on ? "ON" : "OFF");
            state.setTextColor(on ? Color.rgb(180, 255, 212) : Color.rgb(230, 220, 230));
            state.setBackground(rounded(on ? Color.rgb(25, 74, 54) : Color.rgb(48, 44, 58),
                    24, on ? Color.rgb(52, 154, 110) : Color.rgb(80, 72, 98), 2));
        }
        hint.setText(menuOpen
                ? "Press Y again to close.  Use the controller pointer to select a button."
                : "Press Y to reopen the menu.");
    }

    private void resetAll() {
        for (String key : featureState.keySet()) featureState.put(key, false);
        SharedPreferences.Editor e = prefs.edit();
        for (String key : featureState.keySet()) e.putBoolean(key, false);
        e.apply();
        renderMenu();
        Toast.makeText(this, "All features OFF", Toast.LENGTH_SHORT).show();
    }

    private void runFeatureSelfTest() {
        boolean ok = true;
        for (String key : featureState.keySet()) {
            boolean before = featureState.get(key);
            featureState.put(key, !before);
            boolean changed = featureState.get(key) == !before;
            featureState.put(key, before);
            ok &= changed;
        }
        diagnostic.setText(ok ? "Self-test: PASS • toggles respond independently" : "Self-test: FAILED");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP &&
                event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_Y) {
            menuOpen = !menuOpen;
            renderMenu();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
