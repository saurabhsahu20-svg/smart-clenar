package com.sclean.cachecleaner;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppsActivity extends Activity {
    private static final class Entry {
        final String name, pkg;
        Entry(String n,String p){name=n;pkg=p;}
    }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Entry> apps=new ArrayList<>();
    private final List<Entry> filtered=new ArrayList<>();
    private BaseAdapter adapter;
    private TextView count;
    private EditText search;
    private PackageManager pm;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pm=getPackageManager();
        LinearLayout root=STheme.column(this);
        root.setBackgroundColor(STheme.bg(this));
        STheme.insets(this,root,18,18,18,0);
        setContentView(root);
        LinearLayout heading=STheme.row(this);
        TextView back=STheme.text(this,"‹",31,STheme.text(this),true);
        back.setWidth(STheme.dp(this,38));
        back.setOnClickListener(v->finish());
        heading.addView(back);
        heading.addView(STheme.text(this," App Manager",24,STheme.text(this),true));
        root.addView(heading);
        STheme.space(this,root,8);
        root.addView(STheme.text(this,"Installed launchable apps. Open their official Android settings to clear individual caches.",
                13,STheme.secondary(this),false));
        STheme.space(this,root,8);
        LinearLayout tip=STheme.panel(this);
        tip.addView(STheme.text(this,"ANDROID SAFE MODE",11,STheme.MINT,true));
        STheme.space(this,tip,5);
        tip.addView(STheme.text(this,
                "Tap an app → Storage & cache → Clear cache. Avoid Clear storage, which can remove saved app data.",
                13,STheme.text(this),false));
        root.addView(tip);
        STheme.space(this,root,16);

        search=new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(STheme.text(this));
        search.setHintTextColor(STheme.secondary(this));
        search.setHint("Search installed apps");
        search.setTextSize(15);
        search.setPadding(STheme.dp(this,15),0,STheme.dp(this,15),0);
        search.setBackground(STheme.rounded(STheme.cardColor(this),16,this));
        root.addView(search,new LinearLayout.LayoutParams(-1,STheme.dp(this,51)));
        STheme.space(this,root,11);
        count=STheme.text(this,"Finding apps…",12,STheme.MINT,true);
        root.addView(count);
        STheme.space(this,root,9);

        ListView list=new ListView(this);
        list.setDividerHeight(0);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setSelector(STheme.rounded(STheme.iconBg(this),12,this));
        adapter=new BaseAdapter() {
            @Override public int getCount(){return filtered.size();}
            @Override public Object getItem(int i){return filtered.get(i);}
            @Override public long getItemId(int i){return i;}
            @Override public View getView(int pos,View reusable,ViewGroup parent){
                Entry item=filtered.get(pos);
                LinearLayout row=STheme.row(AppsActivity.this);
                row.setPadding(STheme.dp(AppsActivity.this,14),STheme.dp(AppsActivity.this,10),
                        STheme.dp(AppsActivity.this,12),STheme.dp(AppsActivity.this,10));
                row.setBackground(STheme.rounded(STheme.cardColor(AppsActivity.this),15,AppsActivity.this));
                ImageView icon=new ImageView(AppsActivity.this);
                try {
                    Drawable picture=pm.getApplicationIcon(item.pkg);
                    icon.setImageDrawable(picture);
                } catch(PackageManager.NameNotFoundException err) {
                    icon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                row.addView(icon,new LinearLayout.LayoutParams(STheme.dp(AppsActivity.this,39),
                        STheme.dp(AppsActivity.this,39)));
                LinearLayout info=STheme.column(AppsActivity.this);
                info.setPadding(STheme.dp(AppsActivity.this,12),0,STheme.dp(AppsActivity.this,7),0);
                TextView name=STheme.text(AppsActivity.this,item.name,15,
                        STheme.text(AppsActivity.this),true);
                name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                info.addView(name);
                TextView packageName=STheme.text(AppsActivity.this,item.pkg,11,
                        STheme.secondary(AppsActivity.this),false);
                packageName.setSingleLine(true);
                packageName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                info.addView(packageName);
                row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
                row.addView(STheme.text(AppsActivity.this,"›",24,STheme.MINT,true));
                LinearLayout wrapper=STheme.column(AppsActivity.this);
                wrapper.setPadding(0,0,0,STheme.dp(AppsActivity.this,9));
                wrapper.addView(row);
                return wrapper;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((a,v,pos,id)->{
            if(pos>=filtered.size())return;
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:"+filtered.get(pos).pkg)));
            } catch(RuntimeException exc) {
                Toast.makeText(this,"App settings unavailable",Toast.LENGTH_LONG).show();
            }
        });
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){filter(s.toString());}
            @Override public void afterTextChanged(Editable e){}
        });
        worker.execute(()->{
            Intent request=new Intent(Intent.ACTION_MAIN);
            request.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> entries;
            try{entries=pm.queryIntentActivities(request,0);}
            catch(RuntimeException e){entries=new ArrayList<>();}
            Set<String> seen=new HashSet<>();
            List<Entry> collected=new ArrayList<>();
            for(ResolveInfo info:entries){
                if(info.activityInfo==null || info.activityInfo.applicationInfo==null)continue;
                String pkg=info.activityInfo.packageName;
                if(pkg==null || pkg.equals(getPackageName()) || !seen.add(pkg))continue;
                CharSequence name;
                try{name=info.activityInfo.applicationInfo.loadLabel(pm);}
                catch(RuntimeException error){name=pkg;}
                collected.add(new Entry(name.toString(),pkg));
            }
            Collections.sort(collected,(a,b)->a.name.compareToIgnoreCase(b.name));
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                apps.clear();
                apps.addAll(collected);
                filter(search.getText().toString());
            });
        });
    }

    private void filter(String term) {
        String query=term.toLowerCase(Locale.ROOT).trim();
        filtered.clear();
        for(Entry e:apps)if(query.isEmpty() ||
                e.name.toLowerCase(Locale.ROOT).contains(query) ||
                e.pkg.toLowerCase(Locale.ROOT).contains(query))filtered.add(e);
        count.setText(filtered.size()+" apps available");
        if(adapter!=null)adapter.notifyDataSetChanged();
    }

    @Override protected void onDestroy(){
        worker.shutdownNow();
        super.onDestroy();
    }
}
