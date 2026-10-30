package io.github.hozdogus.boxdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** First screen: sets up storage, ROMs and the default machine, then starts 86Box. */
public class LauncherActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button accessBtn, romsBtn, startBtn;
    private boolean downloading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---------------------------------------------------------------- UI

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sizeSp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setBackgroundColor(getColor(R.color.win_grey));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private View buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(32), dp(24), dp(32));

        TextView title = text("86BoxDroid", 30, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(title);
        TextView sub = text("Windows 95/98-era PC emulator (86Box)", 15, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(sub);

        status = text("", 16, false);
        col.addView(status);

        accessBtn = button("1. Allow file access", v -> askForAccess());
        romsBtn   = button("2. Download ROMs (about 100–300 MB)", v -> downloadRoms());
        startBtn  = button("Start PC", v -> startPc());
        col.addView(accessBtn);
        col.addView(romsBtn);
        col.addView(startBtn);

        col.addView(text(
                "Controls\n"
                + "• Drag one finger: move the mouse\n"
                + "• Tap: left click   • Two-finger tap: right click\n"
                + "• Tap then touch and drag: hold the left button\n"
                + "• Three-finger tap: on-screen keyboard\n"
                + "• Back button: menu (mount CDs/floppies, reset, quit)\n\n"
                + "Files live in /sdcard/86BoxDroid:\n"
                + "• isos/ – put your Windows 95/98 CD image (.iso) here\n"
                + "• floppies/ – boot floppy images (.img)\n"
                + "• vm/ – the PC's settings (86box.cfg) and 2 GB hard disk\n\n"
                + "Default PC: Pentium MMX 166, 64 MB RAM, S3 Trio64V2, Sound Blaster 16.\n"
                + "A Bluetooth or USB keyboard and mouse also work.",
                14, false));

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        return sv;
    }

    private void refresh() {
        boolean access = Storage.hasAccess();
        if (access) Storage.makeFolders();
        boolean roms = access && Storage.hasRoms();

        StringBuilder sb = new StringBuilder();
        sb.append(access ? "✔ File access allowed\n" : "✘ File access needed\n");
        sb.append(roms ? "✔ ROMs installed\n" : "✘ ROMs missing (BIOS files for the emulated PCs)\n");
        if (access) sb.append(Storage.hasMachine() ? "✔ PC set up" : "• Default PC will be created on first start");
        if (!downloading) status.setText(sb.toString());

        accessBtn.setVisibility(access ? View.GONE : View.VISIBLE);
        romsBtn.setEnabled(access && !downloading);
        romsBtn.setText(roms ? "Re-download ROMs" : "2. Download ROMs (about 100–300 MB)");
        startBtn.setEnabled(access && roms && !downloading);
    }

    // ----------------------------------------------------------- actions

    private void askForAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[] {
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
        }
    }

    private void startPc() {
        try {
            Storage.createDefaultMachine(this);
        } catch (IOException e) {
            showError("Couldn't create the default PC: " + e.getMessage());
            return;
        }
        startActivity(new Intent(this, EmulatorActivity.class));
    }

    private void showError(String msg) {
        new AlertDialog.Builder(this).setTitle("86BoxDroid").setMessage(msg)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void setStatus(String s) {
        ui.post(() -> status.setText(s));
    }

    private void downloadRoms() {
        downloading = true;
        refresh();
        setStatus("Finding the latest ROM set…");
        new Thread(() -> {
            String error = null;
            try {
                fetchAndExtractRoms();
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String err = error;
            ui.post(() -> {
                downloading = false;
                refresh();
                if (err != null) showError("ROM download failed: " + err);
            });
        }, "rom-download").start();
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "86BoxDroid");
        return c;
    }

    private void fetchAndExtractRoms() throws Exception {
        // This app tracks 86Box's main branch, so use the matching ROM set.
        String tag = "master";
        String zipUrl = "https://github.com/86Box/roms/archive/refs/heads/master.zip";

        File dest = Storage.roms();
        dest.mkdirs();
        String destPath = dest.getCanonicalPath() + File.separator;

        HttpURLConnection dl = open(zipUrl);
        long total = dl.getContentLengthLong();
        long[] read = {0};
        byte[] buf = new byte[1 << 16];
        int files = 0;
        try (InputStream raw = new BufferedInputStream(dl.getInputStream(), 1 << 16);
             InputStream counted = new CountingStream(raw, read);
             ZipInputStream zip = new ZipInputStream(counted)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                // GitHub zips wrap everything in "86Box-roms-<hash>/"; drop that.
                String name = e.getName();
                int slash = name.indexOf('/');
                if (slash < 0) continue;
                name = name.substring(slash + 1);
                if (name.isEmpty()) continue;
                File out = new File(dest, name);
                if (!out.getCanonicalPath().startsWith(destPath)) continue; // zip-slip guard
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zip.read(buf)) > 0) os.write(buf, 0, n);
                }
                if ((++files % 50) == 0) {
                    String mb = (read[0] >> 20) + " MB" + (total > 0 ? " of " + (total >> 20) + " MB" : "");
                    setStatus("Downloading ROMs " + tag + "…\n" + mb + " • " + files + " files");
                }
            }
        }
        if (files == 0) throw new IOException("the download was empty");
    }

    /** Counts bytes read so the download can show progress. */
    private static final class CountingStream extends java.io.FilterInputStream {
        private final long[] counter;
        CountingStream(InputStream in, long[] counter) { super(in); this.counter = counter; }
        @Override public int read() throws IOException {
            int b = super.read();
            if (b >= 0) counter[0]++;
            return b;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) counter[0] += n;
            return n;
        }
    }
}
