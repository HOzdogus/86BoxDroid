package io.github.hozdogus.boxdroid;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Where 86BoxDroid keeps its files: /sdcard/86BoxDroid/{roms,vm,isos,floppies}. */
final class Storage {
    /** 2 GB IDE disk: 4161 cylinders x 16 heads x 63 sectors x 512 bytes. */
    static final long HDD_BYTES = 4161L * 16L * 63L * 512L;

    private Storage() {}

    static boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return Environment.isExternalStorageManager();
        return true; // pre-Android 11: covered by the legacy storage permissions
    }

    static File root()     { return new File(Environment.getExternalStorageDirectory(), "86BoxDroid"); }
    static File roms()     { return new File(root(), "roms"); }
    static File vm()       { return new File(root(), "vm"); }
    static File isos()     { return new File(root(), "isos"); }
    static File floppies() { return new File(root(), "floppies"); }
    static File config()   { return new File(vm(), "86box.cfg"); }
    static File hdd()      { return new File(vm(), "win95.img"); }

    static void makeFolders() {
        root().mkdirs();
        roms().mkdirs();
        vm().mkdirs();
        isos().mkdirs();
        floppies().mkdirs();
    }

    /** The 86Box ROM set has a "machines" folder at its top level. */
    static boolean hasRoms() {
        File machines = new File(roms(), "machines");
        String[] list = machines.list();
        return list != null && list.length > 0;
    }

    static boolean hasMachine() {
        return config().exists();
    }

    /** Writes the default Windows 95/98 machine and a blank 2 GB hard disk. */
    static void createDefaultMachine(Context ctx) throws IOException {
        makeFolders();
        if (!config().exists()) {
            String cfg;
            try (InputStream in = ctx.getAssets().open("default-86box.cfg")) {
                cfg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            cfg = cfg.replace("@HDD@", hdd().getAbsolutePath());
            try (FileOutputStream out = new FileOutputStream(config())) {
                out.write(cfg.getBytes(StandardCharsets.UTF_8));
            }
        }
        if (!hdd().exists()) {
            // Sparse file: takes almost no real space until Windows fills it.
            try (RandomAccessFile raf = new RandomAccessFile(hdd(), "rw")) {
                raf.setLength(HDD_BYTES);
            }
        }
    }
}
