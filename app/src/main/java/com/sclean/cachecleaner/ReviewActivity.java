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
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.view.ViewGroup;
import java.util.LinkedHashMap;
import java.util.concurrent.Future;
import android.os.Environment;
import java.io.File;
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
    private final LinkedHashMap<String, FileScan.Entry> selected=new LinkedHashMap<>();
    private BaseAdapter adapter;
    private TextView selectAllButton, clearButton, renameButton, rescanButton;
    private Future<?> scanTask;
    private boolean deleting=false;
    private ListView fileList;
    private LinearLayout bars;
    private TextView description, count, bottomButton, folderButton, status;
    private FileScan.Result results;
    private String mode=JUNK;
    private String selectedFolder=null;
    private boolean scanDevice=false; // Permissioned shared storage, not Android-private folders.
    private int jobVersion=0;
    private boolean working=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String requested=getIntent().getStringExtra(EXTRA_MODE);
        if (requested!=null) mode=requested;
        selectedFolder=getSharedPreferences("s_cleaner",MODE_PRIVATE).getString("folder",null);
        scanDevice=getIntent().getBooleanExtra("scan_device",false) && hasDeviceAccess();
        buildUi();
        if(scanDevice || selectedFolder!=null) scanFolder();
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
            t.setOnClickListener(v->{
                if(working || deleting) {
                    Toast.makeText(this,"Complete the current operation first",Toast.LENGTH_SHORT).show();
                    return;
                }
                mode=label;
                updateResults();
            });
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
        if(hasDeviceAccess()) {
            STheme.space(this,root,8);
            TextView fullScan=STheme.pill(this,"◎ Scan all accessible shared files",false);
            root.addView(fullScan);
            fullScan.setOnClickListener(v->{
                scanDevice=true;
                scanFolder();
            });
        }
        STheme.space(this,root,12);

        description=STheme.text(this,"Select a folder to review its files.",13,STheme.secondary(this),false);
        root.addView(description);
        STheme.space(this,root,10);
        count=STheme.text(this,"0 files",15,STheme.MINT,true);
        root.addView(count);
        STheme.space(this,root,10);
        LinearLayout selectionBar=STheme.row(this);
        selectAllButton=STheme.pill(this,"Select All",false);
        clearButton=STheme.pill(this,"Clear",false);
        renameButton=STheme.pill(this,"Rename",false);
        TextView[] options={selectAllButton,clearButton,renameButton};
        for(int i=0;i<options.length;i++) {
            TextView button=options[i];
            button.setTextSize(12);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,STheme.dp(this,45),1);
            if(i>0)params.leftMargin=STheme.dp(this,6);
            selectionBar.addView(button,params);
        }
        root.addView(selectionBar);
        selectAllButton.setOnClickListener(v->selectAll());
        clearButton.setOnClickListener(v->clearSelected());
        renameButton.setOnClickListener(v->renameSelected());
        STheme.space(this,root,8);
        bars=STheme.column(this);
        root.addView(bars);
        STheme.space(this,root,8);

        fileList=new ListView(this);
        fileList.setChoiceMode(ListView.CHOICE_MODE_NONE);
        fileList.setDividerHeight(0);
        fileList.setCacheColorHint(Color.TRANSPARENT);
        fileList.setBackground(STheme.rounded(STheme.cardColor(this),20,this));
        // Virtualized rows: no 70,000-element caption array on the UI thread.
        adapter=new BaseAdapter() {
            @Override public int getCount(){return showing.size();}
            @Override public Object getItem(int position){return showing.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View reuse,ViewGroup parent) {
                CheckedTextView label=(reuse instanceof CheckedTextView)
                        ?(CheckedTextView)reuse
                        :(CheckedTextView)getLayoutInflater().inflate(
                                android.R.layout.simple_list_item_multiple_choice,parent,false);
                FileScan.Entry e=showing.get(position);
                String name=e.name.length()>74?e.name.substring(0,71)+"…":e.name;
                String place=e.location.length()>90?
                        "…"+e.location.substring(e.location.length()-87):e.location;
                label.setText(name+"\n"+size(e.bytes)+" • "+place+
                        (e.deletable?"":"\nProtected · read-only"));
                label.setTextColor(e.deletable?STheme.text(ReviewActivity.this):
                        STheme.secondary(ReviewActivity.this));
                label.setTextSize(13);
                label.setPadding(STheme.dp(ReviewActivity.this,14),
                        STheme.dp(ReviewActivity.this,10),STheme.dp(ReviewActivity.this,14),
                        STheme.dp(ReviewActivity.this,10));
                label.setChecked(selected.containsKey(key(e)));
                label.setEnabled(true);
                return label;
            }
        };
        fileList.setAdapter(adapter);
        root.addView(fileList,new LinearLayout.LayoutParams(-1,0,1));
        fileList.setOnItemClickListener((a,b,pos,id)->{
            if(working || deleting || pos<0 || pos>=showing.size())return;
            FileScan.Entry e=showing.get(pos);
            if(!e.deletable) {
                Toast.makeText(this,"This item is read-only",Toast.LENGTH_SHORT).show();
                return;
            }
            String k=key(e);
            if(selected.containsKey(k))selected.remove(k);
            else selected.put(k,e);
            // Only visible rows are rebound; no full scan of checkbox states.
            adapter.notifyDataSetChanged();
            updateSelection();
        });
        STheme.space(this,root,10);

        LinearLayout buttons=STheme.row(this);
        rescanButton=STheme.pill(this,"↻ Rescan",false);
        buttons.addView(rescanButton,new LinearLayout.LayoutParams(0,-2,1));
        rescanButton.setOnClickListener(v->{
            if(deleting) return;
            scanFolder();
        });
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

    // The flags below are masked with Android's two grant bits; lint cannot infer the bitmask.
    @android.annotation.SuppressLint("WrongConstant")
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_FOLDER || result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri picked=data.getData();
        int flags=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(picked,flags);
            selectedFolder=picked.toString();
            scanDevice=false;
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
                intro="Storage breakdown of accessible shared files. Only selected files can be deleted.";
                break;
            default:
                intro="Potential junk: .tmp, .log, .bak and incomplete downloads. This does not include other apps’ private caches. No files are selected automatically.";
        }
        description.setText(intro);
    }

    private boolean hasDeviceAccess() {
        if(android.os.Build.VERSION.SDK_INT>=30)
            return android.os.Environment.isExternalStorageManager();
        return android.os.Build.VERSION.SDK_INT<23 ||
                checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        ==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void scanFolder() {
        if(scanDevice && !hasDeviceAccess()) {
            scanDevice=false;
            status.setText("Device access is not granted");
            Toast.makeText(this,"Open S Cleaner and grant shared storage access first",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if(!scanDevice && selectedFolder==null) {
            status.setText("No folder selected");
            Toast.makeText(this,"Choose a permitted folder to scan",Toast.LENGTH_SHORT).show();
            return;
        }
        if(deleting) {
            Toast.makeText(this,"Finish file changes before rescanning",Toast.LENGTH_SHORT).show();
            return;
        }
        if(scanTask!=null)scanTask.cancel(true);
        final boolean full=scanDevice;
        final int version=++jobVersion;
        final Uri uri=full?null:Uri.parse(selectedFolder);
        working=true;
        results=null;
        showing.clear();
        selected.clear();
        adapter.notifyDataSetChanged();
        fileList.setEnabled(false);
        folderButton.setEnabled(false);
        status.setText(full?"Scanning accessible shared storage…":"Scanning selected folder…");
        count.setText("Scanning safely in background");
        updateSelection();
        scanTask=worker.submit(()->{
            FileScan.Result data=null;
            Exception failure=null;
            try {
                data=full?DeviceScan.scan(getApplicationContext()):
                        FileScan.scan(getApplicationContext(),uri);
            } catch(Exception exc){failure=exc;}
            final FileScan.Result scan=data;
            final Exception error=failure;
            runOnUiThread(()->{
                if(version!=jobVersion || isFinishing() || isDestroyed())return;
                working=false;
                fileList.setEnabled(true);
                folderButton.setEnabled(true);
                updateSelection();
                if(error!=null) {
                    status.setText("Scan unavailable");
                    results=null;
                    description.setText("Storage permission may have been revoked, or the folder is unavailable. Choose a different folder or enable storage access.");
                    Toast.makeText(this,"Scan failed: check Android storage access",
                            Toast.LENGTH_LONG).show();
                    updateResults();
                } else {
                    results=scan;
                    if(full) {
                        status.setText("Shared storage · accessible files only");
                    } else {
                        String last=DocumentsContract.getTreeDocumentId(uri);
                        if(last.length()>50)last="…"+last.substring(last.length()-47);
                        status.setText(last);
                    }
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
        selected.clear();
        bars.removeAllViews();
        if(results==null) {
            adapter.notifyDataSetChanged();
            count.setText(selectedFolder==null?"Choose a folder above":"No scan results yet");
            updateSelection();
            return;
        }
        showing.addAll(currentEntries());
        long total=0;
        for(FileScan.Entry e: showing) if(e.bytes>0)total+=e.bytes;
        adapter.notifyDataSetChanged();
        String scanState=results.partial?"  •  Partial scan (limit / inaccessible folders)":"";
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

    private static String key(FileScan.Entry e) {return e.uri.toString();}

    private void clearSelected() {
        if(working || deleting)return;
        selected.clear();
        adapter.notifyDataSetChanged();
        updateSelection();
    }

    private void selectAll() {
        if(working || deleting || showing.isEmpty())return;
        int eligible=0;
        for(FileScan.Entry e:showing)if(e.deletable)eligible++;
        if(eligible==0) {
            Toast.makeText(this,"No editable files available",Toast.LENGTH_SHORT).show();
            return;
        }
        // Selecting all does not delete anything. Always confirm mass deletion separately.
        if(eligible>1000) {
            int count=eligible;
            new AlertDialog.Builder(this)
                    .setTitle("Select "+count+" files?")
                    .setMessage("This selects every editable file in the current "+mode+
                            " list. No files are deleted until you confirm. Review before deleting.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Select All",(d,w)->doSelectAll())
                    .show();
        } else doSelectAll();
    }

    private void doSelectAll() {
        if(working || deleting)return;
        selected.clear();
        for(FileScan.Entry e:showing)if(e.deletable)selected.put(key(e),e);
        adapter.notifyDataSetChanged();
        updateSelection();
    }

    private void updateSelection() {
        long bytes=0;
        for(FileScan.Entry e:selected.values())bytes+=Math.max(0L,e.bytes);
        int chosen=selected.size();
        boolean busy=working||deleting;
        bottomButton.setEnabled(!busy && chosen>0);
        bottomButton.setAlpha(!busy && chosen>0 ? 1f : .55f);
        bottomButton.setText(busy?(deleting?"Applying changes…":"Scanning…"):
                (chosen>0 ? "Delete "+chosen+" selected · "+size(bytes) : "Select files manually"));
        selectAllButton.setEnabled(!busy && !showing.isEmpty());
        clearButton.setEnabled(!busy && chosen>0);
        renameButton.setEnabled(!busy && chosen==1);
        rescanButton.setEnabled(!deleting);
        renameButton.setAlpha(!busy && chosen==1 ? 1f : .50f);
    }

    private void confirmDelete() {
        if(working || deleting || selected.isEmpty())return;
        List<FileScan.Entry> selectedFiles=new ArrayList<>(selected.values());
        long size=0;
        for(FileScan.Entry e:selectedFiles)size+=Math.max(0,e.bytes);
        final long bytes=size;
        new AlertDialog.Builder(this)
            .setTitle("Delete "+selectedFiles.size()+" selected files?")
            .setMessage("You selected "+selectedFiles.size()+" files totaling "+size(bytes)+
                    ". This can permanently delete personal files and cannot be undone. Continue only if you no longer need them.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Review deletion",(dialog,which)->{
                // A second confirmation is especially important for bulk operations.
                new AlertDialog.Builder(this)
                        .setTitle("Final confirmation")
                        .setMessage("Permanently delete "+selectedFiles.size()+" files? This is NOT a cache-only cleanup.")
                        .setNegativeButton("Keep files",null)
                        .setPositiveButton("Delete selected",(d,w)->deleteFiles(selectedFiles))
                        .show();
            })
            .show();
    }

    private void deleteFiles(List<FileScan.Entry> selected) {
        deleting=true;
        updateSelection();
        worker.execute(()->{
            long[] stats=FileScan.delete(getApplicationContext(),selected);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                deleting=false;
                updateSelection();
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

    private void renameSelected() {
        if(working || deleting || selected.size()!=1)return;
        FileScan.Entry entry=selected.values().iterator().next();
        EditText editor=new EditText(this);
        editor.setSingleLine(true);
        editor.setText(entry.name);
        editor.setSelectAllOnFocus(true);
        LinearLayout container=STheme.column(this);
        container.setPadding(STheme.dp(this,22),STheme.dp(this,5),
                STheme.dp(this,22),0);
        container.addView(editor);
        new AlertDialog.Builder(this)
                .setTitle("Rename file")
                .setMessage("Choose a new filename. File contents will not change.")
                .setView(container)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Rename",(dialog,which)->{
                    String proposed=editor.getText().toString().trim();
                    if(!isValidName(proposed)) {
                        Toast.makeText(this,"Invalid filename. Avoid /, \\, :, and special characters.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if(proposed.equals(entry.name))return;
                    performRename(entry,proposed);
                }).show();
    }

    private static boolean isValidName(String value) {
        if(value.isEmpty() || value.equals(".") || value.equals("..") ||
                value.length()>180 || value.endsWith(".") || value.endsWith(" "))return false;
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c<' ' || c== '/' || c=='\\' || c==':' ||
                    c=='*' || c=='?' || c=='"' || c=='<' || c=='>' || c=='|')return false;
        }
        return true;
    }

    private void performRename(FileScan.Entry entry,String newName) {
        if(deleting || working)return;
        deleting=true;
        updateSelection();
        worker.execute(()->{
            boolean success=false;
            try {
                if(entry.localFile!=null) {
                    if(android.os.Build.VERSION.SDK_INT<30 ||
                            Environment.isExternalStorageManager()) {
                        File current=entry.localFile;
                        File parent=current.getParentFile();
                        File root=Environment.getExternalStorageDirectory();
                        File target=new File(parent,newName);
                        String base=root.getCanonicalPath()+File.separator;
                        String src=current.getCanonicalPath();
                        String dest=target.getCanonicalPath();
                        if(src.startsWith(base) && dest.startsWith(base) &&
                                src.equals(current.getAbsolutePath()) &&
                                dest.equals(target.getAbsolutePath()) &&
                                parent.isDirectory() && current.isFile() &&
                                !target.exists())success=current.renameTo(target);
                    }
                } else {
                    Uri renamed=DocumentsContract.renameDocument(
                            getContentResolver(),entry.uri,newName);
                    success=renamed!=null;
                }
            } catch(Exception ignored) {
                success=false;
            }
            final boolean okay=success;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                deleting=false;
                updateSelection();
                Toast.makeText(this,okay?"File renamed successfully":
                        "Rename unavailable for this file or provider",Toast.LENGTH_LONG).show();
                scanFolder();
            });
        });
    }

    private String size(long bytes) {
        return bytes<0?"Unknown size":Formatter.formatFileSize(this,bytes);
    }

    @Override protected void onDestroy() {
        ++jobVersion;
        if(scanTask!=null)scanTask.cancel(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
