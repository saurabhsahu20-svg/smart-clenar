package com.sclean.cachecleaner;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppsActivity extends Activity {
    private static final class Entry {
        final String name;
        final String pkg;
        Entry(String name, String pkg) { this.name = name; this.pkg = pkg; }
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView count;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F7FC);
        root.setPadding(dp(18), dp(18), dp(18), 0);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                v.setPadding(dp(18) + bars.left, dp(18) + bars.top,
                        dp(18) + bars.right, bars.bottom);
                return insets;
            });
        }
        TextView back = text("‹   " + getString(R.string.apps_title), 23, 0xFF253A73);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        TextView info = text(getString(R.string.apps_help), 14, 0xFF66748C);
        info.setPadding(0, dp(16), 0, dp(16));
        root.addView(info);
        EditText search = new EditText(this);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setHint(R.string.search_apps);
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(search);
        count = text("…", 13, 0xFF66748C);
        count.setPadding(0, dp(10), 0, dp(10));
        root.addView(count);
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new ArrayList<String>());
        list.setAdapter(adapter);
        list.setDividerHeight(0);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= shown.size()) return;
            Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + shown.get(position).pkg));
            try { startActivity(settings); }
            catch (RuntimeException e) {
                Toast.makeText(this, R.string.error_settings, Toast.LENGTH_SHORT).show();
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        worker.execute(() -> {
            PackageManager pm = getPackageManager();
            Intent request = new Intent(Intent.ACTION_MAIN);
            request.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> activities;
            try { activities = pm.queryIntentActivities(request, 0); }
            catch (RuntimeException e) { activities = new ArrayList<>(); }
            Set<String> seen = new HashSet<>();
            List<Entry> found = new ArrayList<>();
            for (ResolveInfo item : activities) {
                if (item.activityInfo == null || item.activityInfo.applicationInfo == null) continue;
                String pkg = item.activityInfo.packageName;
                if (pkg == null || pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                CharSequence label;
                try { label = item.activityInfo.applicationInfo.loadLabel(pm); }
                catch (RuntimeException e) { label = pkg; }
                found.add(new Entry(label.toString(), pkg));
            }
            found.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                all.clear();
                all.addAll(found);
                filter(search.getText().toString());
            });
        });
    }

    private void filter(String phrase) {
        String term = phrase.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        adapter.clear();
        for (Entry entry : all) {
            if (term.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(term)
                    || entry.pkg.toLowerCase(Locale.ROOT).contains(term)) {
                shown.add(entry);
                adapter.add(entry.name + "\n" + entry.pkg);
            }
        }
        count.setText(getString(R.string.apps_count, shown.size()));
        adapter.notifyDataSetChanged();
    }

    private TextView text(String text, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private int dp(float d) { return Math.round(getResources().getDisplayMetrics().density * d); }
    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
