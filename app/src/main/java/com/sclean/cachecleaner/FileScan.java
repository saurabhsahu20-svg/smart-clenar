package com.sclean.cachecleaner;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class FileScan {
    // Bound scans to protect battery, memory, and cloud data usage.
    static final int MAX_ENTRIES = 5000;
    static final int MAX_DEPTH = 12;
    static final long LARGE_THRESHOLD = 50L * 1024L * 1024L;
    static final long MAX_HASH_FILE = 120L * 1024L * 1024L;
    static final long MAX_HASH_TOTAL = 650L * 1024L * 1024L;
    static final String[] PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_FLAGS
    };

    static final class Entry {
        final Uri uri;
        final String name;
        final String location;
        final String mime;
        final long bytes;
        final boolean deletable;
        Entry(Uri uri, String name, String location, String mime, long bytes, boolean deletable) {
            this.uri=uri; this.name=name; this.location=location;
            this.mime=mime; this.bytes=bytes; this.deletable=deletable;
        }
    }

    static final class Result {
        final List<Entry> all=new ArrayList<>();
        final List<Entry> junk=new ArrayList<>();
        final List<Entry> large=new ArrayList<>();
        final List<Entry> duplicates=new ArrayList<>();
        final LinkedHashMap<String,Long> categories=new LinkedHashMap<>();
        long totalBytes;
        long duplicateBytes;
        boolean partial;
        int unreadable;
        int skippedHash;
        Result() {
            categories.put("Images",0L); categories.put("Videos",0L);
            categories.put("Audio",0L); categories.put("Documents",0L);
            categories.put("Archives",0L); categories.put("Other",0L);
        }
    }

    private static final class Dir {
        final String docId, relative;
        final int depth;
        Dir(String id, String rel, int depth) { this.docId=id; this.relative=rel; this.depth=depth; }
    }

    private FileScan() {}

    static Result scan(Context context, Uri treeUri) throws Exception {
        ContentResolver resolver=context.getContentResolver();
        String rootId=DocumentsContract.getTreeDocumentId(treeUri);
        Result output=new Result();
        ArrayDeque<Dir> pending=new ArrayDeque<>();
        pending.add(new Dir(rootId,"",0));
        Set<String> visited=new HashSet<>();
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) { output.partial=true; break; }
            Dir folder=pending.removeFirst();
            if (!visited.add(folder.docId)) continue;
            Uri childUri=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,folder.docId);
            Cursor cursor=null;
            try {
                cursor=resolver.query(childUri,PROJECTION,null,null,null);
                if (cursor==null) { output.partial=true; output.unreadable++; continue; }
                int idCol=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameCol=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeCol=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
                int sizeCol=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);
                int flagsCol=cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS);
                while(cursor.moveToNext()) {
                    if (output.all.size()+visited.size()+pending.size() >= MAX_ENTRIES) {
                        output.partial=true; pending.clear(); break;
                    }
                    String id=cursor.getString(idCol);
                    String name=cursor.getString(nameCol);
                    String mime=cursor.getString(mimeCol);
                    if (id==null || name==null) continue;
                    String path=folder.relative.isEmpty()?name:folder.relative+"/"+name;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        if (folder.depth < MAX_DEPTH) pending.addLast(new Dir(id,path,folder.depth+1));
                        else output.partial=true;
                    } else {
                        long len=cursor.isNull(sizeCol)?-1L:cursor.getLong(sizeCol);
                        int flags=cursor.isNull(flagsCol)?0:cursor.getInt(flagsCol);
                        Uri uri=DocumentsContract.buildDocumentUriUsingTree(treeUri,id);
                        Entry entry=new Entry(uri,name,path,mime,len,
                                (flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE)!=0);
                        output.all.add(entry);
                        if (len>0) output.totalBytes+=len;
                        if (len>=LARGE_THRESHOLD) output.large.add(entry);
                        if (isJunk(name)) output.junk.add(entry);
                        String category=category(name,mime);
                        if (len>0) output.categories.put(category,
                                output.categories.get(category)+len);
                    }
                }
            } catch (SecurityException e) {
                output.partial=true;
                output.unreadable++;
                // Inaccessible folder? Continue with accessible folders only.
            } catch (RuntimeException e) {
                output.partial=true;
                output.unreadable++;
            } finally {
                if(cursor!=null)cursor.close();
            }
        }
        Collections.sort(output.large, (a,b)->Long.compare(b.bytes,a.bytes));
        findDuplicates(resolver,output);
        return output;
    }

    private static boolean isJunk(String filename) {
        String lower=filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".tmp") || lower.endsWith(".temp")
                || lower.endsWith(".bak") || lower.endsWith(".old")
                || lower.endsWith(".log") || lower.endsWith(".part")
                || lower.endsWith(".crdownload") || lower.endsWith(".download")
                || lower.equals("thumbs.db") || lower.equals(".ds_store")
                || lower.startsWith("~$");
    }

    static String category(String name,String mime) {
        String m=mime==null?"":mime.toLowerCase(Locale.ROOT);
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        if(m.startsWith("image/")||ends(n,".jpg",".jpeg",".png",".gif",".webp",".heic")) return "Images";
        if(m.startsWith("video/")||ends(n,".mp4",".mkv",".mov",".avi",".webm"))return "Videos";
        if(m.startsWith("audio/")||ends(n,".mp3",".wav",".flac",".aac",".ogg"))return "Audio";
        if(ends(n,".zip",".rar",".7z",".tar",".gz",".apk"))return "Archives";
        if(m.startsWith("text/")||ends(n,".pdf",".doc",".docx",".xls",".xlsx",".ppt",".pptx",".txt",".csv")) return "Documents";
        return "Other";
    }

    private static boolean ends(String name,String... suffixes) {
        for(String item:suffixes)if(name.endsWith(item))return true;
        return false;
    }

    private static void findDuplicates(ContentResolver resolver, Result result) {
        Map<Long,List<Entry>> sameSize=new HashMap<>();
        for (Entry e:result.all) {
            if (e.bytes>0) {
                List<Entry> bucket=sameSize.get(e.bytes);
                if(bucket==null) { bucket=new ArrayList<>(); sameSize.put(e.bytes,bucket); }
                bucket.add(e);
            }
        }
        long hashed=0;
        for (Map.Entry<Long,List<Entry>> group:sameSize.entrySet()) {
            List<Entry> values=group.getValue();
            if (values.size()<2) continue;
            HashMap<String,Entry> keepOne=new HashMap<>();
            for (Entry e:values) {
                if (Thread.currentThread().isInterrupted()) { result.partial=true; return; }
                if (e.bytes>MAX_HASH_FILE || hashed+e.bytes>MAX_HASH_TOTAL) {
                    result.skippedHash++;
                    continue;
                }
                hashed+=e.bytes;
                try {
                    String signature=sha256(resolver,e.uri);
                    if(signature!=null && keepOne.containsKey(signature)) {
                        result.duplicates.add(e);
                        result.duplicateBytes+=e.bytes;
                    } else if(signature!=null) {
                        keepOne.put(signature,e);
                    } else { result.skippedHash++; }
                } catch (Exception error) { result.skippedHash++; }
            }
        }
    }

    private static String sha256(ContentResolver resolver,Uri uri) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[65536];
        long seen=0;
        try(InputStream stream=resolver.openInputStream(uri)) {
            if(stream==null) return null;
            try(BufferedInputStream in=new BufferedInputStream(stream,65536)) {
                int count;
                while ((count=in.read(buffer))!=-1) {
                    if (Thread.currentThread().isInterrupted()) return null;
                    digest.update(buffer,0,count);
                    seen+=count;
                    if(seen>MAX_HASH_FILE) return null;
                }
            }
        }
        byte[] raw=digest.digest();
        StringBuilder out=new StringBuilder(raw.length*2);
        for(byte b:raw)out.append(String.format(Locale.US,"%02x",b & 0xff));
        return out.toString();
    }

    static long[] delete(Context context, List<Entry> entries) {
        int successes=0, failures=0;
        long bytes=0L;
        for(Entry e: entries) {
            try {
                if (e.deletable && DocumentsContract.deleteDocument(context.getContentResolver(),e.uri)) {
                    successes++;
                    bytes+=Math.max(0L,e.bytes);
                } else failures++;
            } catch (Exception exc) { failures++; }
        }
        return new long[]{successes,failures,bytes};
    }
}
