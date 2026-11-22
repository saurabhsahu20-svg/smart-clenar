package com.sclean.cachecleaner;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private StorageRingView ring;
    private TextView storageCaption, available, occupied, cacheLabel, cleanBtn, monitorText;
    private boolean cleanupBusy=false;
    private boolean active=false;
    private static final int LEGACY_FILES_PERMISSION=108;
    private static final int SYSTEM_CACHE_REQUEST=109;
    private boolean launchFullScanAfterSettings=false;
    private boolean launchSystemCacheAfterSettings=false;
    private final Runnable monitor=new Runnable() {
        @Override public void run() {
            if(!active)return;
            updateUsage();
            ui.postDelayed(this,30000L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        drawPage();
        if(getIntent()!=null && getIntent().getBooleanExtra("open_system_cache",false)) {
            // Explicitly requested from App Manager: show Android's cache confirmation flow.
            getIntent().removeExtra("open_system_cache");
            ui.post(this::openSystemCache);
        }
    }

    private void drawPage() {
        boolean dark=STheme.dark(this);
        getWindow().setStatusBarColor(STheme.bg(this));
        getWindow().setNavigationBarColor(STheme.bg(this));
        if (Build.VERSION.SDK_INT>=23) {
            getWindow().getDecorView().setSystemUiVisibility(
                    dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(STheme.bg(this));
        STheme.insets(this,frame,0,0,0,0);
        setContentView(frame);
        ScrollView scroll=new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        int max=STheme.dp(this,760);
        int screen=getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(
                Math.min(max,screen),ViewGroup.LayoutParams.MATCH_PARENT,Gravity.CENTER_HORIZONTAL);
        frame.addView(scroll,sp);

        LinearLayout page=STheme.column(this);
        page.setPadding(STheme.dp(this,20),STheme.dp(this,23),
                STheme.dp(this,20),STheme.dp(this,32));
        scroll.addView(page);
        header(page);
        STheme.space(this,page,22);
        hero(page);
        STheme.space(this,page,18);
        action(page);
        STheme.space(this,page,14);
        fullStorageAction(page);
        STheme.space(this,page,25);

        LinearLayout heading=STheme.row(this);
        TextView label=STheme.text(this,"Cleaning toolkit",21,STheme.text(this),true);
        heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(STheme.text(this,"8 TOOLS",11,STheme.MINT,true));
        page.addView(heading);
        STheme.space(this,page,6);
        page.addView(STheme.text(this,"Powerful tools. You stay in control.",
                13,STheme.secondary(this),false));
        STheme.space(this,page,14);

        toolRow(page,"clean","System Cache","Android confirmation",
                this::openSystemCache,
                "junk","Junk Files","Review potential junk",
                ()->openReview(ReviewActivity.JUNK));
        toolRow(page,"dupes","Duplicates","Content-verified",
                ()->openReview(ReviewActivity.DUPES),
                "large","Large Files","Find 50 MB+ files",
                ()->openReview(ReviewActivity.LARGE));
        toolRow(page,"analyze","Storage Analyzer","File type breakdown",
                ()->openReview(ReviewActivity.ANALYZE),
                "download","Downloads","Open download manager",
                this::openDownloads);
        toolRow(page,"apps","App Manager","Manage installed apps",
                ()->startActivity(new Intent(this,AppsActivity.class)),
                "monitor","Storage Monitor","System storage settings",
                this::openStorage);

        STheme.space(this,page,18);
        LinearLayout live=STheme.panel(this);
        LinearLayout liveTop=STheme.row(this);
        TextView dot=STheme.text(this,"●",17,STheme.MINT,true);
        liveTop.addView(dot);
        TextView title=STheme.text(this,"  LIVE STORAGE MONITOR",12,STheme.text(this),true);
        liveTop.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView refresh=STheme.text(this,"↻ Refresh",12,STheme.MINT,true);
        refresh.setPadding(STheme.dp(this,10),STheme.dp(this,6),
                STheme.dp(this,4),STheme.dp(this,6));
        refresh.setOnClickListener(v->updateUsage());
        liveTop.addView(refresh);
        live.addView(liveTop);
        STheme.space(this,live,9);
        monitorText=STheme.text(this,"Monitoring internal storage while app is open.",
                14,STheme.secondary(this),false);
        live.addView(monitorText);
        page.addView(live);

        STheme.space(this,page,18);
        LinearLayout privacy=STheme.panel(this);
        LinearLayout privacyHeading=STheme.row(this);
        TextView privacyIcon=STheme.text(this,"✓",22,STheme.MINT,true);
        privacyHeading.addView(privacyIcon);
        privacyHeading.addView(STheme.text(this,"   Privacy-first protection",16,STheme.text(this),true));
        privacy.addView(privacyHeading);
        STheme.space(this,privacy,8);
        privacy.addView(STheme.text(this,
                "No ads and no tracking. Optional All files access scans shared storage only. You select each file and confirm deletion.",
                13,STheme.secondary(this),false));
        STheme.space(this,privacy,9);
        privacy.addView(STheme.text(this,
                "Private app folders remain protected. The system cache tool requests Android's confirmation, while App Manager opens individual app Settings.",
                12,STheme.secondary(this),false));
        page.addView(privacy);
        STheme.space(this,page,19);
        TextView footer=STheme.text(this,"S CLEANER  •  VERSION 2.4  •  MADE FOR ANDROID",
                11,STheme.secondary(this),true);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer);

        page.setAlpha(0f); page.setTranslationY(STheme.dp(this,12));
        page.animate().alpha(1f).translationY(0).setDuration(380).start();
    }

    private void header(LinearLayout parent) {
        LinearLayout row=STheme.row(this);
        FrameLayout brand=new FrameLayout(this);
        brand.setBackground(STheme.gradient(this,STheme.MINT,0xFF16A6C3,17));
        TextView letter=STheme.text(this,"S",25,STheme.INK,true);
        letter.setGravity(Gravity.CENTER);
        brand.addView(letter,new FrameLayout.LayoutParams(-1,-1));
        row.addView(brand,new LinearLayout.LayoutParams(STheme.dp(this,52),STheme.dp(this,52)));
        LinearLayout words=STheme.column(this);
        words.setPadding(STheme.dp(this,12),0,0,0);
        words.addView(STheme.text(this,"S Cleaner",25,STheme.text(this),true));
        words.addView(STheme.text(this,"Your space. Simplified.",12,STheme.secondary(this),false));
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView switchTheme=STheme.text(this,STheme.dark(this)?"☀":"☾",25,
                STheme.MINT,true);
        switchTheme.setGravity(Gravity.CENTER);
        switchTheme.setContentDescription("Switch light or dark theme");
        switchTheme.setBackground(STheme.rounded(STheme.cardColor(this),16,this));
        row.addView(switchTheme,new LinearLayout.LayoutParams(STheme.dp(this,49),STheme.dp(this,49)));
        switchTheme.setOnClickListener(v->{
            STheme.setDark(this,!STheme.dark(this));
            recreate();
        });
        parent.addView(row);
    }

    private void hero(LinearLayout parent) {
        LinearLayout card=STheme.column(this);
        card.setPadding(STheme.dp(this,22),STheme.dp(this,20),
                STheme.dp(this,22),STheme.dp(this,20));
        card.setBackground(STheme.gradient(this,0xFF153B55,0xFF0A233B,30));
        TextView heading=STheme.text(this,"DEVICE STORAGE",12,0xFF9CECD9,true);
        card.addView(heading);
        STheme.space(this,card,4);
        card.addView(STheme.text(this,"Everything at a glance",20,Color.WHITE,true));
        ring=new StorageRingView(this);
        card.addView(ring,new LinearLayout.LayoutParams(-1,STheme.dp(this,216)));
        storageCaption=STheme.text(this,"Loading device storage…",12,0xFFC6DFE2,false);
        storageCaption.setGravity(Gravity.CENTER);
        card.addView(storageCaption);
        STheme.space(this,card,18);
        LinearLayout stats=STheme.row(this);
        LinearLayout left=stat("AVAILABLE", "—");
        LinearLayout right=stat("IN USE", "—");
        available=(TextView)left.getChildAt(1);
        occupied=(TextView)right.getChildAt(1);
        stats.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        View separator=new View(this);
        separator.setBackgroundColor(0xFF275267);
        stats.addView(separator,new LinearLayout.LayoutParams(STheme.dp(this,1),STheme.dp(this,48)));
        stats.addView(right,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(stats);
        parent.addView(card);
    }

    private LinearLayout stat(String title,String number) {
        LinearLayout result=STheme.column(this);
        TextView first=STheme.text(this,title,11,0xFF8FC5C8,true);
        first.setGravity(Gravity.CENTER);
        result.addView(first);
        TextView second=STheme.text(this,number,20,Color.WHITE,true);
        second.setGravity(Gravity.CENTER);
        result.addView(second);
        return result;
    }

    private void action(LinearLayout parent) {
        LinearLayout card=STheme.panel(this);
        LinearLayout row=STheme.row(this);
        TextView symbol=STheme.text(this,"✦",22,STheme.MINT,true);
        row.addView(symbol);
        TextView title=STheme.text(this,"  Safe Cache Cleanup",17,STheme.text(this),true);
        row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(row);
        STheme.space(this,card,7);
        card.addView(STheme.text(this,
                "Clear S Cleaner's own temporary cache below. Use Android's system confirmation to clear supported external caches from other apps.",
                13,STheme.secondary(this),false));
        STheme.space(this,card,11);
        cacheLabel=STheme.text(this,"S Cleaner cache: checking…",13,STheme.MINT,true);
        card.addView(cacheLabel);
        STheme.space(this,card,14);
        cleanBtn=STheme.pill(this,"✦   CLEAR S CLEANER CACHE",true);
        card.addView(cleanBtn);
        cleanBtn.setOnClickListener(v->cleanOwn());
        STheme.space(this,card,11);
        TextView system=STheme.pill(this,"◎   CLEAR SYSTEM EXTERNAL CACHES",false);
        card.addView(system);
        system.setOnClickListener(v->openSystemCache());
        parent.addView(card);
    }


    private void fullStorageAction(LinearLayout page) {
        LinearLayout box=STheme.panel(this);
        box.addView(STheme.text(this,"◎  SCAN DEVICE STORAGE",17,STheme.text(this),true));
        STheme.space(this,box,8);
        box.addView(STheme.text(this,
                "Scan accessible files in shared storage for junk, duplicates and large files. Private app folders remain protected. Nothing is deleted automatically.",
                13,STheme.secondary(this),false));
        STheme.space(this,box,12);
        TextView start=STheme.pill(this,"SCAN SHARED FILES",true);
        box.addView(start);
        start.setOnClickListener(v->openFullStorageScan());
        STheme.space(this,box,7);
        TextView alternative=STheme.text(this,"Or select a folder (no broad permission)",
                12,STheme.MINT,true);
        alternative.setPadding(0,STheme.dp(this,8),0,STheme.dp(this,8));
        alternative.setOnClickListener(v->openReview(ReviewActivity.ANALYZE,false));
        box.addView(alternative);
        page.addView(box);
    }

    private boolean hasAllFileAccess() {
        if(Build.VERSION.SDK_INT>=30) return Environment.isExternalStorageManager();
        if(Build.VERSION.SDK_INT>=23) return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                ==PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private void openFullStorageScan() {
        if(hasAllFileAccess()) openReview(ReviewActivity.ANALYZE,true);
        else askStoragePermission(false);
    }

    private void openSystemCache() {
        if(Build.VERSION.SDK_INT<30) {
            new AlertDialog.Builder(this)
                    .setTitle("Clear other apps' cache")
                    .setMessage("This Android version does not support the system-wide external cache confirmation screen. Open App Manager and clear cache for each app from Settings.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("App Manager",(d,w)->
                            startActivity(new Intent(this,AppsActivity.class)))
                    .show();
            return;
        }
        if(!hasAllFileAccess()) { askStoragePermission(true); return; }
        try {
            Intent intent=new Intent(StorageManager.ACTION_CLEAR_APP_CACHE);
            startActivityForResult(intent,SYSTEM_CACHE_REQUEST);
        } catch(RuntimeException e) {
            new AlertDialog.Builder(this)
                    .setTitle("System cache screen unavailable")
                    .setMessage("This device has not provided the external-cache cleanup dialog. Clear apps individually in Android Settings.")
                    .setPositiveButton("App Manager",(d,w)->
                            startActivity(new Intent(this,AppsActivity.class)))
                    .setNegativeButton("Cancel",null).show();
        }
    }

    private void askStoragePermission(boolean forSystemCache) {
        new AlertDialog.Builder(this)
                .setTitle("Optional storage access")
                .setMessage(forSystemCache
                        ? "Android requires All files access to open its external app-cache cleanup confirmation. It does not delete private app data automatically. Grant only if you are comfortable."
                        : "Scanning shared files across the device needs optional All files access. Files stay on this device; no personal files are deleted without choosing them and confirming. Private app folders remain protected. You can select a single folder instead.")
                .setNegativeButton("Not now",null)
                .setNeutralButton("Choose folder",(d,w)->openReview(ReviewActivity.ANALYZE,false))
                .setPositiveButton("Open Android permissions",(d,w)->{
                    launchFullScanAfterSettings=!forSystemCache;
                    launchSystemCacheAfterSettings=forSystemCache;
                    if(Build.VERSION.SDK_INT>=30) {
                        try {
                            Intent settings=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:"+getPackageName()));
                            startActivity(settings);
                        } catch(RuntimeException error) {
                            try{startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
                            catch(RuntimeException ignored) {
                                launchFullScanAfterSettings=false;
                                launchSystemCacheAfterSettings=false;
                                Toast.makeText(this,"Storage access settings unavailable",Toast.LENGTH_LONG).show();
                            }
                        }
                    } else if(Build.VERSION.SDK_INT>=23) {
                        requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },LEGACY_FILES_PERMISSION);
                    }
                }).show();
    }

    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==LEGACY_FILES_PERMISSION) {
            if(hasAllFileAccess()) {
                if(launchSystemCacheAfterSettings)openSystemCache();
                else if(launchFullScanAfterSettings)openReview(ReviewActivity.ANALYZE,true);
            } else Toast.makeText(this,"Permission denied; use Choose folder instead.",Toast.LENGTH_LONG).show();
            launchFullScanAfterSettings=false;
            launchSystemCacheAfterSettings=false;
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);
        if(request==SYSTEM_CACHE_REQUEST) {
            if(result==RESULT_OK) Toast.makeText(this,
                    "Android confirmed external app caches cleaned",Toast.LENGTH_LONG).show();
            else Toast.makeText(this,"External cache cleanup not completed",Toast.LENGTH_SHORT).show();
            updateUsage();
        }
    }

    private void toolRow(LinearLayout page, String i1,String t1,String s1,Runnable a1,
                         String i2,String t2,String s2,Runnable a2) {
        LinearLayout row=STheme.row(this);
        View first=toolCard(i1,t1,s1,a1);
        View second=toolCard(i2,t2,s2,a2);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,STheme.dp(this,158),1);
        lp.rightMargin=STheme.dp(this,6);
        row.addView(first,lp);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,STheme.dp(this,158),1);
        rp.leftMargin=STheme.dp(this,6);
        row.addView(second,rp);
        page.addView(row);
        STheme.space(this,page,12);
    }

    private View toolCard(String icon,String name,String sub,Runnable action) {
        LinearLayout card=STheme.column(this);
        card.setPadding(STheme.dp(this,15),STheme.dp(this,15),
                STheme.dp(this,13),STheme.dp(this,13));
        card.setBackground(STheme.rounded(STheme.cardColor(this),22,this));
        if(!STheme.dark(this))card.setElevation(STheme.dp(this,2));
        FrameLayout emblem=new FrameLayout(this);
        emblem.setBackground(STheme.rounded(STheme.iconBg(this),12,this));
        STheme.Icon iconView=new STheme.Icon(this,icon);
        FrameLayout.LayoutParams iLp=new FrameLayout.LayoutParams(
                STheme.dp(this,32),STheme.dp(this,32),Gravity.CENTER);
        emblem.addView(iconView,iLp);
        card.addView(emblem,new LinearLayout.LayoutParams(STheme.dp(this,48),STheme.dp(this,48)));
        STheme.space(this,card,10);
        TextView title=STheme.text(this,name,15,STheme.text(this),true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title);
        STheme.space(this,card,3);
        TextView description=STheme.text(this,sub,12,STheme.secondary(this),false);
        description.setMaxLines(2);
        card.addView(description);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(name+". "+sub);
        card.setOnClickListener(v->action.run());
        return card;
    }

    private void updateUsage() {
        long[] values=CacheTools.storage();
        long total=values[0], free=values[1];
        if(total>0) {
            free=Math.min(total,Math.max(0,free));
            long used=total-free;
            if(ring!=null)ring.setPercent(used*100d/total);
            available.setText(Formatter.formatFileSize(this,free));
            occupied.setText(Formatter.formatFileSize(this,used));
            storageCaption.setText(Formatter.formatFileSize(this,total)+" TOTAL CAPACITY");
            monitorText.setText(Formatter.formatFileSize(this,free)+" free of "+
                    Formatter.formatFileSize(this,total)+" • refreshed while the app is visible");
        }
        refreshCache();
    }

    private void refreshCache() {
        if(cleanupBusy)return;
        executor.execute(()->{
            long bytes=CacheTools.scanOrClean(getApplicationContext(),false);
            ui.post(()->{
                if(!isFinishing()&&!isDestroyed()&&!cleanupBusy&&cacheLabel!=null)
                    cacheLabel.setText("S Cleaner cache: "+Formatter.formatFileSize(this,bytes));
            });
        });
    }

    private void cleanOwn() {
        if(cleanupBusy)return;
        cleanupBusy=true;
        cleanBtn.setEnabled(false);
        cleanBtn.setAlpha(.6f);
        cleanBtn.setText("CLEANING TEMPORARY FILES…");
        executor.execute(()->{
            long cleared=CacheTools.scanOrClean(getApplicationContext(),true);
            ui.post(()->{
                if(isFinishing()||isDestroyed())return;
                cleanupBusy=false;
                cleanBtn.setEnabled(true);cleanBtn.setAlpha(1f);
                cleanBtn.setText("✦   CLEAN SAFELY");
                Toast.makeText(this,cleared>0
                        ? "Safe cleanup: "+Formatter.formatFileSize(this,cleared)+" freed"
                        : "S Cleaner cache is empty; other apps' caches were not scanned.",Toast.LENGTH_LONG).show();
                updateUsage();
            });
        });
    }

    private void openReview(String mode) {
        openReview(mode,hasAllFileAccess());
    }

    private void openReview(String mode,boolean allStorage) {
        Intent i=new Intent(this,ReviewActivity.class);
        i.putExtra(ReviewActivity.EXTRA_MODE,mode);
        i.putExtra("scan_device",allStorage);
        startActivity(i);
    }
    private void openStorage() {
        Intent preferred=new Intent(Build.VERSION.SDK_INT>=25?
                StorageManager.ACTION_MANAGE_STORAGE:Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        try{startActivity(preferred);}
        catch(RuntimeException e) {
            try{startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));}
            catch(RuntimeException ignored){Toast.makeText(this,"Storage settings unavailable",
                    Toast.LENGTH_SHORT).show();}
        }
    }
    private void openDownloads() {
        try{startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));}
        catch(RuntimeException e) {
            try{
                Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.setType("*/*");
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                startActivity(picker);
            }catch(RuntimeException ignored){
                Toast.makeText(this,"Downloads unavailable",Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        active=true;
        ui.removeCallbacks(monitor);
        monitor.run();
        if((launchFullScanAfterSettings||launchSystemCacheAfterSettings)
                && hasAllFileAccess()) {
            boolean all=launchFullScanAfterSettings;
            launchFullScanAfterSettings=false;
            boolean cache=launchSystemCacheAfterSettings;
            launchSystemCacheAfterSettings=false;
            if(cache)openSystemCache();
            else if(all)openReview(ReviewActivity.ANALYZE,true);
        }
    }
    @Override protected void onPause() {
        active=false;
        ui.removeCallbacks(monitor);
        super.onPause();
    }
    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
