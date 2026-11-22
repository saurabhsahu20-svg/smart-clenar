package com.sclean.cachecleaner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.UriPermission;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReviewActivity extends Activity {
    static final String EXTRA_MODE = "tool";
    static final String JUNK="Junk", LARGE="Large", DUPES="Duplicates", ANALYZE="Analyze";
    private static final int PICK_FOLDER=17;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<FileScan.Entry> showing=new ArrayList<>();
    private final List<String> captions=new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ListView fileList;
    private LinearLayout bars;
    private TextView description, count, bottomButton, folderButton, status;
    private FileScan.Result results;
    private String mode=JUNK;
    private String selectedFolder=null;
    private int jobVersion=0;
    private boolean working=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String requested=getIntent().getStringExtra(EXTRA_MODE);
        if (requested!=null) mode=requested;
        selectedFolder=getPreferences(0).getString("ignored",null);
        selectedFolder=getSharedPreferences("s_cleaner",MODE_PRIVATE).getString("folder",null);
        buildUi();
        if(selectedFolder!=null) scanFolder();
    }

    private void buildUi() {
        LinearLayout root=STheme.column(this);
        root.setBackgroundColor(STheme.bg(this));
        STheme.insets(this,root,18,16,18,12);
        setContentView(root);

        LinearLayout toolbar=STheme.row(this);
        TextView back=STheme.text(this,"‹",30,STheme.text(this),true);
        back.setGravity(Gravity.CENTER);
        back.setWidth(STheme.dp(this,34));
        back.setOnClickListener(v->finish());
        toolbar.addView(back);
        TextView title=STheme.text(this,"  File Explorer",24,STheme.text(this),true);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(toolbar);
        STheme.space(this,root,8);
        root.addView(STheme.text(this,"PRIVATE FOLDER SCAN  •  NO CLOUD UPLOAD",
                10,STheme.MINT,true));
        STheme.space(this,root,14);

        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=STheme.row(this);
        for(String label:new String[]{JUNK,LARGE,DUPES,ANALYZE}) {
            TextView t=STheme.pill(this,label,false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,STheme.dp(this,46));
            lp.rightMargin=STheme.dp(this,8);
            tabs.addView(t,lp);
            t.setOnClickListener(v->{mode=label;updateResults();});
        }
        scroll.addView(tabs);
        root.addView(scroll);
        STheme.space(this,root,12);

        LinearLayout folderRow=STheme.row(this);
        LinearLayout textBox=STheme.column(this);
        textBox.addView(STheme.text(this,"Selected folder",12,STheme.secondary(this),false));
        status=STheme.text(this,"No folder selected",13,STheme.text(this),true);
        status.setMaxLines(2); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textBox.addView(status);
        folderRow.addView(textBox,new LinearLayout.LayoutParams(0,-2,1));
        folderButton=STheme.pill(this,"Choose folder",true);
        folderRow.addView(folderButton);
        folderButton.setOnClickListener(v->pickFolder());
        root.addView(folderRow);
        STheme.space(this,root,12);

        description=STheme.text(this,"Select a folder to review its files.",13,STheme.secondary(this),false);
        root.addView(description);
        STheme.space(this,root,10);
        count=STheme.text(this,"0 files",15,STheme.MINT,true);
        root.addView(count);
        STheme.space(this,root,8);
        bars=STheme.column(this);
        root.addView(bars);
        STheme.space(this,root,8);

        fileList=new ListView(this);
        fileList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        fileList.setDividerHeight(0);
        fileList.setCacheColorHint(Color.TRANSPARENT);
        fileList.setBackground(STheme.rounded(STheme.cardColor(this),20,this));
        adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_multiple_choice,captions);
        fileList.setAdapter(adapter);
        root.addView(fileList,new LinearLayout.LayoutParams(-1,0,1));
        fileList.setOnItemClickListener((a,b,pos,id)->updateSelection());
        STheme.space(this,root,10);

        LinearLayout buttons=STheme.row(this);
        TextView rescan=STheme.pill(this,"↻ Rescan",false);
        buttons.addView(rescan,new LinearLayout.LayoutParams(0,-2,1));
        rescan.setOnClickListener(v->scanFolder());
        View space=new View(this);
        buttons.addView(space,new LinearLayout.LayoutParams(STheme.dp(this,10),1));
        bottomButton=STheme.pill(this,"Select files",true);
        buttons.addView(bottomButton,new LinearLayout.LayoutParams(0,-2,2));
        bottomButton.setOnClickListener(v->confirmDelete());
        root.addView(buttons);
        refreshIntro();
    }

    private void pickFolder() {
        try {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent,PICK_FOLDER);
        } catch (RuntimeException error) {
            Toast.makeText(this,"Folder picker is unavailable on this device",Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_FOLDER || result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri picked=data.getData();
        int flags=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(picked,flags);
            selectedFolder=picked.toString();
            getSharedPreferences("s_cleaner",MODE_PRIVATE).edit()
                    .putString("folder",selectedFolder).apply();
            scanFolder();
        } catch (SecurityException e) {
            Toast.makeText(this,"Folder access was not granted. Please choose another folder.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refreshIntro() {
        String intro;
        switch (mode) {
            case LARGE:
                intro="Files at least 50 MB, sorted by size. Choose what you no longer need.";
                break;
            case DUPES:
                intro="Verified SHA-256 duplicates only. One copy of each group is excluded from this list.";
                break;
            case ANALYZE:
                intro="Storage breakdown of the selected folder. Check files before deleting.";
                break;
            default:
                intro="Potential junk: .tmp, .log, .bak and incomplete downloads. Nothing is selected automatically.";
        }
        description.setText(intro);
    }

    private void scanFolder() {
        if(selectedFolder==null) {
            status.setText("No folder selected");
            Toast.makeText(this,"Choose a permitted folder to scan",Toast.LENGTH_SHORT).show();
            return;
        }
        final int version=++jobVersion;
        final Uri uri=Uri.parse(selectedFolder);
        working=true;
        fileList.setEnabled(false);
        folderButton.setEnabled(false);
        status.setText("Scanning selected folder…");
        count.setText("Scanning safely in background");
        bottomButton.setText("Scanning…");
        worker.execute(()->{
            FileScan.Result data=null;
            Exception failure=null;
            try { data=FileScan.scan(getApplicationContext(),uri); }
            catch(Exception exc){failure=exc;}
            final FileScan.Result scan=data;
            final Exception error=failure;
            runOnUiThread(()->{
                if(version!=jobVersion || isFinishing() || isDestroyed())return;
                working=false;
                fileList.setEnabled(true);
                folderButton.setEnabled(true);
                if(error!=null) {
                    status.setText("Could not access folder");
                    description.setText("Android may have revoked folder access. Choose the folder again.");
                    Toast.makeText(this,"Scan failed: permission or provider error",Toast.LENGTH_LONG).show();
                } else {
                    results=scan;
                    String last=DocumentsContract.getTreeDocumentId(uri);
                    if(last.length()>50)last="…"+last.substring(last.length()-47);
                    status.setText(last);
                    updateResults();
                }
            });
        });
    }

    private List<FileScan.Entry> currentEntries() {
        if(results==null)return new ArrayList<>();
        switch(mode) {
            case LARGE: return results.large;
            case DUPES: return results.duplicates;
            case ANALYZE: return results.all;
            default: return results.junk;
        }
    }

    private void updateResults() {
        refreshIntro();
        showing.clear();
        captions.clear();
        fileList.clearChoices();
        bars.removeAllViews();
        if(results==null) {
            adapter.notifyDataSetChanged();
            count.setText(selectedFolder==null?"Choose a folder above":"No scan results yet");
            updateSelection();
            return;
        }
        showing.addAll(currentEntries());
        long total=0;
        for(FileScan.Entry e: showing) {
            if(e.bytes>0)total+=e.bytes;
            String name=e.name.length()>65 ? e.name.substring(0,62)+"…" : e.name;
            String location=e.location;
            if(location.length()>80)location="…"+location.substring(location.length()-77);
            captions.add(name+"\n"+size(e.bytes)+"  ·  "+location+(e.deletable?"":"\nRead only"));
        }
        adapter.notifyDataSetChanged();
        String scanState=results.partial?"  •  Partial scan":"";
        if(results.skippedHash>0 && mode.equals(DUPES))
            scanState+="  •  "+results.skippedHash+" not hash-checked";
        count.setText(String.format(Locale.US,"%d files  •  %s%s",
                showing.size(),size(total),scanState));
        if(mode.equals(ANALYZE))drawBars();
        updateSelection();
    }

    private void drawBars() {
        long total=0;
        for(long value:results.categories.values())total+=value;
        if(total<=0)return;
        for(Map.Entry<String,Long> section: results.categories.entrySet()) {
            LinearLayout row=STheme.row(this);
            TextView name=STheme.text(this,section.getKey(),12,STheme.secondary(this),false);
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(STheme.text(this,size(section.getValue()),12,STheme.text(this),true));
            bars.addView(row);
            View track=new View(this);
            android.graphics.drawable.GradientDrawable outline=STheme.rounded(
                    STheme.dark(this)?0xFF20435A:0xFFDDE8EA,4,this);
            track.setBackground(outline);
            LinearLayout barRoot=STheme.row(this);
            barRoot.setBackground(outline);
            int pct=(int)Math.min(100,Math.round(100d*section.getValue()/total));
            View fill=new View(this);
            fill.setBackground(STheme.rounded(STheme.MINT,4,this));
            barRoot.addView(fill,new LinearLayout.LayoutParams(0,STheme.dp(this,5),pct));
            barRoot.addView(new View(this),new LinearLayout.LayoutParams(0,1,100-pct));
            LinearLayout.LayoutParams bLp=new LinearLayout.LayoutParams(-1,STheme.dp(this,5));
            bLp.topMargin=STheme.dp(this,4); bLp.bottomMargin=STheme.dp(this,8);
            bars.addView(barRoot,bLp);
        }
    }

    private void updateSelection() {
        long bytes=0; int chosen=0;
        for(int i=0;i<showing.size();i++) {
            if(fileList.isItemChecked(i)) { chosen++; bytes+=Math.max(0,showing.get(i).bytes); }
        }
        if(working) {
            bottomButton.setText("Scanning…");
            bottomButton.setEnabled(false);
        } else {
            bottomButton.setEnabled(chosen>0);
            bottomButton.setAlpha(chosen>0?1f:.55f);
            bottomButton.setText(chosen>0 ? "Delete "+chosen+" selected · "+size(bytes) : "Select files to delete");
        }
    }

    private void confirmDelete() {
        if(working)return;
        List<FileScan.Entry> selected=new ArrayList<>();
        long size=0;
        for(int i=0;i<showing.size();i++)if(fileList.isItemChecked(i)) {
            FileScan.Entry e=showing.get(i);
            selected.add(e);
            size+=Math.max(0,e.bytes);
        }
        if(selected.isEmpty())return;
        final long bytes=size;
        new AlertDialog.Builder(this)
            .setTitle("Delete "+selected.size()+" selected files?")
            .setMessage("These files total "+size(bytes)+". Deletion may be permanent and cannot be undone. Confirm only if you no longer need these personal files.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Delete files",(dialog,which)->deleteFiles(selected))
            .show();
    }

    private void deleteFiles(List<FileScan.Entry> selected) {
        working=true;
        updateSelection();
        worker.execute(()->{
            long[] stats=FileScan.delete(getApplicationContext(),selected);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                working=false;
                new AlertDialog.Builder(this)
                    .setTitle("Cleanup finished")
                    .setMessage(stats[0]+" files deleted ("+size(stats[2])+")."+
                            (stats[1]>0?"\n"+stats[1]+" could not be deleted by the document provider.":""))
                    .setPositiveButton("OK",(d,w)->scanFolder())
                    .setCancelable(false)
                    .show();
            });
        });
    }

    private String size(long bytes) {
        return bytes<0?"Unknown size":Formatter.formatFileSize(this,bytes);
    }

    @Override protected void onDestroy() {
        ++jobVersion;
        worker.shutdownNow();
        super.onDestroy();
    }
}
