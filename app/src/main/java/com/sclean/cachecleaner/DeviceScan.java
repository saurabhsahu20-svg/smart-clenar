package com.sclean.cachecleaner;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Foreground, opt-in scanner for the primary shared storage volume.
 * No app-private paths, no network, no automatic deletion.
 * All-files access on Android 11+ is a user-approved special permission.
 */
final class DeviceScan {
    static final int MAX_FILES = 70000;
    static final int MAX_DEPTH = 28;
    private static final int MAX_DIRECTORIES = 12000;

    private static final class Folder {
        final File file;
        final int depth;
        Folder(File f,int d) { file=f; depth=d; }
    }

    private DeviceScan() {}

    static FileScan.Result scan(Context context) throws IOException {
        if(android.os.Build.VERSION.SDK_INT>=30 &&
                !Environment.isExternalStorageManager()) {
            throw new SecurityException("All files access is not enabled");
        }
        File root=Environment.getExternalStorageDirectory();
        if(root==null || !root.isDirectory() || !root.canRead())
            throw new IOException("Shared storage is unavailable");

        String rootPath=root.getCanonicalPath();
        FileScan.Result out=new FileScan.Result();
        ArrayDeque<Folder> pending=new ArrayDeque<>();
        Set<String> seen=new HashSet<>();
        pending.add(new Folder(root,0));
        while(!pending.isEmpty()) {
            if(Thread.currentThread().isInterrupted()) {
                out.partial=true; break;
            }
            if(out.all.size()>=MAX_FILES || seen.size()>=MAX_DIRECTORIES) {
                out.partial=true; break;
            }
            Folder current=pending.removeFirst();
            String folderPath;
            try { folderPath=current.file.getCanonicalPath(); }
            catch(IOException exc) { out.partial=true; out.unreadable++; continue; }
            if(!folderPath.equals(current.file.getAbsolutePath()))continue;
            if(!folderPath.equals(rootPath) && !folderPath.startsWith(rootPath+File.separator))continue;
            if(!seen.add(folderPath))continue;
            File[] files=current.file.listFiles();
            if(files==null) { out.partial=true; out.unreadable++; continue; }
            for(File f:files) {
                if(Thread.currentThread().isInterrupted()) { out.partial=true; break; }
                if(out.all.size()>=MAX_FILES) { out.partial=true; pending.clear(); break; }
                String actual;
                try { actual=f.getCanonicalPath(); }
                catch(IOException exc) { out.partial=true; out.unreadable++; continue; }
                // Exclude links, mounts outside the primary volume and private Android areas.
                if(!actual.equals(f.getAbsolutePath()) ||
                        !actual.startsWith(rootPath+File.separator)) continue;
                String relative=actual.substring(rootPath.length()+1);
                if(relative.equals("Android/data")||relative.startsWith("Android/data/")||
                        relative.equals("Android/obb")||relative.startsWith("Android/obb/")) continue;
                if(f.isDirectory()) {
                    if(current.depth>=MAX_DEPTH)out.partial=true;
                    else pending.add(new Folder(f,current.depth+1));
                } else if(f.isFile()) {
                    long bytes=f.length();
                    FileScan.Entry e=new FileScan.Entry(f,relative,bytes);
                    out.all.add(e);
                    out.totalBytes+=Math.max(0,bytes);
                    if(bytes>=FileScan.LARGE_THRESHOLD)out.large.add(e);
                    if(FileScan.isJunk(e.name))out.junk.add(e);
                    String category=FileScan.category(e.name,e.mime);
                    out.categories.put(category,out.categories.get(category)+Math.max(0,bytes));
                }
            }
        }
        out.large.sort((a,b)->Long.compare(b.bytes,a.bytes));
        FileScan.findDuplicates(context.getContentResolver(),out);
        return out;
    }
}
