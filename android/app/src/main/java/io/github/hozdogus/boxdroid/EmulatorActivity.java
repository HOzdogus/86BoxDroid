package io.github.hozdogus.boxdroid;

import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.KeyEvent;

import org.libsdl.app.SDLActivity;

/** Runs 86Box (libmain.so) full screen through SDL3. */
public class EmulatorActivity extends SDLActivity {
    private static final String TAG = "86BoxDroid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 86Box's Unix code looks for its data/config folders via these.
        try {
            Os.setenv("HOME", Storage.root().getAbsolutePath(), true);
            Os.setenv("XDG_DATA_HOME", Storage.root().getAbsolutePath(), true);
            Os.setenv("XDG_CONFIG_HOME", Storage.root().getAbsolutePath(), true);
            Os.setenv("TMPDIR", getCacheDir().getAbsolutePath(), true);
            // Where the menu's file browser starts (see osd_core.cpp).
            Os.setenv("BOXDROID_ISO_DIR", Storage.isos().getAbsolutePath(), true);
            Os.setenv("BOXDROID_FLOPPY_DIR", Storage.floppies().getAbsolutePath(), true);
        } catch (ErrnoException e) {
            Log.w(TAG, "setenv failed", e);
        }
        super.onCreate(savedInstanceState);
    }

    /**
     * Back opens/closes 86Box's menu. We catch it here ourselves (rather than
     * relying on SDL forwarding KEYCODE_BACK) and send it on as a MENU key.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
                sendMenuKey();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Reached on some Android versions/gesture setups instead of a key event.
        sendMenuKey();
    }

    private static void sendMenuKey() {
        try {
            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_MENU);
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_MENU);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "native side not ready", e);
        }
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL3", "main" };
    }

    @Override
    protected String[] getArguments() {
        return new String[] {
            "-P", Storage.vm().getAbsolutePath(),
            "-R", Storage.roms().getAbsolutePath(),
        };
    }
}
