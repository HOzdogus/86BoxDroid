package io.github.hozdogus.boxdroid;

import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

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
        } catch (ErrnoException e) {
            Log.w(TAG, "setenv failed", e);
        }
        super.onCreate(savedInstanceState);
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
