package com.sclean.cachecleaner;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BLUE = 0xFF3454E9;
    private static final int NAVY = 0xFF1C2B4F;
    private static final int MUTE = 0xFF68758D;
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView storage, appCache, cleanButton;
    private volatile boolean cleaning = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF5F7FC);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        page.setPadding(p, dp(24), p, dp(32));
        scroll.addView(page);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        page.addView(text("S Cache Cleaner", 27, NAVY, true));
        page.addView(text(getString(R.string.tagline), 14, MUTE, false));
        space(page, 18);
        LinearLayout hero = panel(0xFF2846DA);
        hero.addView(text(getString(R.string.storage_title), 13, Color.WHITE, true));
        space(hero, 10);
        storage = text("…", 17, Color.WHITE, true);
        hero.addView(storage);
        page.addView(hero);
        space(page, 16);
        TextView privacy = text("✓  " + getString(R.string.privacy), 12, BLUE, true);
        privacy.setGravity(Gravity.CENTER);
        page.addView(privacy);
        space(page, 24);

        page.addView(text(getString(R.string.cache_title), 20, NAVY, true));
        space(page, 10);
        LinearLayout own = panel(Color.WHITE);
        own.addView(text(getString(R.string.cache_description), 14, MUTE, false));
        space(own, 14);
        appCache = text("…", 15, BLUE, true);
        own.addView(appCache);
        space(own, 15);
        cleanButton = button(getString(R.string.clean_now), BLUE, Color.WHITE);
        own.addView(cleanButton);
        cleanButton.setOnClickListener(v -> clearOwn());
        page.addView(own);
        space(page, 16);

        LinearLayout others = panel(Color.WHITE);
        others.addView(text(getString(R.string.other_apps), 19, NAVY, true));
        space(others, 8);
        others.addView(text(getString(R.string.other_apps_desc), 14, MUTE, false));
        space(others, 14);
        TextView browse = button(getString(R.string.browse_apps), 0xFFEAF0FF, BLUE);
        others.addView(browse);
        browse.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        page.addView(others);
        space(page, 24);

        page.addView(text(getString(R.string.tools_title), 20, NAVY, true));
        space(page, 10);
        TextView openStorage = button(getString(R.string.storage_settings), Color.WHITE, NAVY);
        page.addView(openStorage);
        openStorage.setOnClickListener(v -> launchStorage());
        space(page, 10);
        TextView downloads = button(getString(R.string.downloads), Color.WHITE, NAVY);
        page.addView(downloads);
        downloads.setOnClickListener(v -> launchDownloads());
        space(page, 22);

        LinearLayout notice = panel(0xFFE8EFFF);
        notice.addView(text("✓  " + getString(R.string.safety_title), 17, NAVY, true));
        space(notice, 6);
        notice.addView(text(getString(R.string.safety_desc), 13, MUTE, false));
        page.addView(notice);
        space(page, 18);
        page.addView(text(getString(R.string.compatibility), 12, MUTE, false));
    }

    @Override protected void onResume() {
        super.onResume();
        long[] s = CacheTools.storage();
        if (storage != null) {
            long total = Math.max(0L, s[0]);
            long free = Math.min(total, Math.max(0L, s[1]));
            storage.setText(total == 0 ? "—" :
                    getString(R.string.free) + ": " + CacheTools.size(this, free) +
                    "    •    " + getString(R.string.used) + ": " + CacheTools.size(this, total - free));
        }
        refreshOwn();
    }

    private void refreshOwn() {
        if (cleaning) return;
        work.execute(() -> {
            long amount = CacheTools.scanOrClean(getApplicationContext(), false);
            ui.post(() -> {
                if (!isFinishing() && !isDestroyed() && !cleaning && appCache != null) {
                    appCache.setText(getString(R.string.cache_size, CacheTools.size(this, amount)));
                }
            });
        });
    }

    private void clearOwn() {
        if (cleaning) return;
        cleaning = true;
        cleanButton.setEnabled(false);
        cleanButton.setAlpha(0.6f);
        cleanButton.setText(R.string.cleaning);
        work.execute(() -> {
            long cleared = CacheTools.scanOrClean(getApplicationContext(), true);
            ui.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                cleaning = false;
                cleanButton.setEnabled(true);
                cleanButton.setAlpha(1f);
                cleanButton.setText(R.string.clean_now);
                Toast.makeText(this, cleared == 0 ? getString(R.string.nothing_to_clean)
                        : getString(R.string.cleaned, CacheTools.size(this, cleared)),
                        Toast.LENGTH_LONG).show();
                onResume();
            });
        });
    }

    private void launchStorage() {
        Intent intent = new Intent(Build.VERSION.SDK_INT >= 25 ?
                StorageManager.ACTION_MANAGE_STORAGE : Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        launch(intent, new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
    }

    private void launchDownloads() {
        Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        fallback.setType("*/*");
        fallback.addCategory(Intent.CATEGORY_OPENABLE);
        launch(intent, fallback);
    }

    private void launch(Intent intent, Intent fallback) {
        try { startActivity(intent); }
        catch (RuntimeException e) {
            try { startActivity(fallback); }
            catch (RuntimeException ignored) {
                Toast.makeText(this, R.string.error_settings, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private LinearLayout panel(int color) {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(20));
        result.setBackground(bg);
        result.setElevation(dp(2));
        return result;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(null, 1);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private TextView button(String s, int color, int foreground) {
        TextView v = text(s, 15, foreground, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(50));
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        v.setBackground(bg);
        return v;
    }
    private void space(LinearLayout parent, int d) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(d)));
    }
    private int dp(float d) { return Math.round(getResources().getDisplayMetrics().density * d); }
    @Override protected void onDestroy() {
        work.shutdown();
        super.onDestroy();
    }
}
