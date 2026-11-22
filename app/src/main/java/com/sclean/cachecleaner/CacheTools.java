package com.sclean.cachecleaner;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CacheTools {
    private static final int MAX_ENTRIES = 150000;
    private CacheTools() { }

    static long[] storage() {
        try {
            StatFs f = new StatFs(Environment.getDataDirectory().getPath());
            return new long[]{f.getTotalBytes(), f.getAvailableBytes()};
        } catch (RuntimeException e) {
            return new long[]{0L, 0L};
        }
    }

    static String size(Context c, long bytes) {
        return Formatter.formatFileSize(c, Math.max(0L, bytes));
    }

    static long scanOrClean(Context context, boolean clean) {
        List<File> roots = new ArrayList<>();
        if (context.getCacheDir() != null) roots.add(context.getCacheDir());
        File[] external = context.getExternalCacheDirs();
        if (external != null) {
            for (File f : external) if (f != null) roots.add(f);
        }
        long total = 0L;
        for (File root : roots) total += process(root, clean);
        return total;
    }

    private static long process(File root, boolean clean) {
        if (root == null || !root.isDirectory()) return 0L;
        try {
            String base = root.getCanonicalPath() + File.separator;
            ArrayDeque<File> todo = new ArrayDeque<>();
            ArrayList<File> directories = new ArrayList<>();
            File[] top = root.listFiles();
            if (top == null) return 0L;
            for (File f : top) todo.addLast(f);
            int seen = 0;
            long total = 0L;
            while (!todo.isEmpty() && seen++ < MAX_ENTRIES) {
                File file = todo.removeFirst();
                if (!file.getCanonicalPath().startsWith(base)) continue;
                if (file.isDirectory()) {
                    directories.add(file);
                    File[] children = file.listFiles();
                    if (children != null) for (File child : children) todo.addLast(child);
                } else {
                    long length = Math.max(0L, file.length());
                    if (!clean || file.delete()) total += length;
                }
            }
            if (clean) {
                java.util.Collections.sort(directories, (a, b) -> Integer.compare(
                        b.getAbsolutePath().length(), a.getAbsolutePath().length()));
                for (File dir : directories) dir.delete();
            }
            return total;
        } catch (IOException | SecurityException e) {
            return 0L;
        }
    }
}
