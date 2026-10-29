package com.zenithuzu.rpgmkport;

import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorkspaceUiTest {
    private void screenshot(Instrumentation ins, String name) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/portemon-ui");
        android.net.Uri uri = ins.getTargetContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try (java.io.OutputStream out = ins.getTargetContext().getContentResolver().openOutputStream(uri)) {
            assertTrue(ins.getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, out));
        }
    }

    @Test public void homeAndProjectNavigationRemainUsable() throws Exception {
        Instrumentation ins = InstrumentationRegistry.getInstrumentation();
        MainActivity a = (MainActivity) ins.startActivitySync(new Intent(ins.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.waitForIdleSync();
        ins.runOnMainSync(() -> {
            assertEquals("Portémon", a.getString(R.string.app_name));
            assertEquals(View.VISIBLE, a.findViewById(R.id.source_zip).getVisibility());
            assertEquals(View.VISIBLE, a.findViewById(R.id.source_folder).getVisibility());
            assertEquals(View.GONE, a.findViewById(R.id.acciones).getVisibility());
            assertEquals(a.findViewById(R.id.source_zip).getHeight(), a.findViewById(R.id.source_folder).getHeight());
        });
        screenshot(ins, "01-inicio");
        ins.runOnMainSync(() -> a.findViewById(R.id.home_start).performClick());
        ins.waitForIdleSync();
        ins.runOnMainSync(() -> {
            assertEquals(View.VISIBLE, a.findViewById(R.id.cabecera).getVisibility());
            assertEquals(View.VISIBLE, a.findViewById(R.id.acciones).getVisibility());
            assertFalse(a.findViewById(R.id.siguiente).isEnabled());
            LinearLayout tabs = a.findViewById(R.id.barras);
            assertEquals(3, tabs.getChildCount());
            assertTrue(tabs.getChildAt(0).isEnabled());
            assertFalse(tabs.getChildAt(1).isEnabled());
            assertFalse(tabs.getChildAt(2).isEnabled());
        });
        screenshot(ins, "02-origen");
        ins.runOnMainSync(() -> a.findViewById(R.id.atras).performClick());
        ins.waitForIdleSync();
        ins.runOnMainSync(() -> {
            assertEquals(a.getString(R.string.workspace_home), ((TextView) a.findViewById(R.id.workspace_title)).getText().toString());
            assertEquals(View.GONE, a.findViewById(R.id.acciones).getVisibility());
            a.finish();
        });
    }
}
