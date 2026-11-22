package com.sclean.cachecleaner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Emulator-only smoke and safety tests.
 * Never accesses data belonging to any other app.
 */
@RunWith(AndroidJUnit4.class)
public final class CleanerSmokeTest {
    @Test
    public void cacheCleanerOnlyRemovesOwnTemporaryFiles() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File temp = new File(c.getCacheDir(), "s_cleaner_emulator_test_tmp");
        File personal = new File(c.getFilesDir(), "s_cleaner_emulator_test_personal");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write("temporary-cache-data".getBytes(StandardCharsets.UTF_8));
            }
            try (FileOutputStream out = new FileOutputStream(personal)) {
                out.write("must-stay".getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(temp.isFile());
            assertTrue(personal.isFile());

            // The app must only scan and delete its own cache dir.
            long preview = CacheTools.scanOrClean(c, false);
            assertTrue("Own cache should be visible", preview >= temp.length());
            long cleaned = CacheTools.scanOrClean(c, true);
            assertTrue("Cache cleaner must delete our test temp file", cleaned > 0);
            assertFalse("Temp file should be gone", temp.exists());
            assertTrue("Permanent app files must remain untouched", personal.isFile());
        } finally {
            temp.delete();
            personal.delete();
        }
    }

    @Test
    public void dashboardStartsWithoutCrashing() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(c, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        assertNotNull(activity);
        assertNotNull(activity.getWindow().getDecorView());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
    }

    @Test
    public void appManagerStartsWithoutCrashing() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(c, AppsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        assertNotNull(activity);
        assertNotNull(activity.getWindow().getDecorView());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
    }
}
