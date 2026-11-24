package com.aldef.power;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

/** Remote TV lewat Wi-Fi (protokol Android TV Remote v2, sama seperti aplikasi Google TV). */
public class RemoteActivity extends Activity implements TvRemote.Listener {

    private static final String PREFS = "tv_remote";
    private static final String K_HOST = "host";
    private static final String K_NAME = "name";
    private static final String K_TOUCH = "touch_mode";

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, String> found = new LinkedHashMap<>();

    private TvRemote remote;
    private TvDiscovery discovery;
    private TvPairing pairing;
    private String host, name;
    private boolean visible, pickerOpen;
    private long lastToast;

    private View connectPanel, remotePanel, stateDot;
    private TextView tvTvName, tvState, tvScan, tvVolume, tabDpad, tabTouch;
    private LinearLayout tvList;
    private DpadView dpad;
    private TouchpadView touchpad;

    private final Runnable retry = () -> {
        if (visible && host != null && pairing == null && !remote.isConnected()) remote.connect(host);
    };

    private final Runnable scanHint = () -> {
        if (pickerOpen && found.isEmpty()) {
            tvScan.setText("Belum ada TV ditemukan. Pastikan TV menyala & satu Wi-Fi, atau masukkan IP manual.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);

        connectPanel = findViewById(R.id.connectPanel);
        remotePanel = findViewById(R.id.remotePanel);
        stateDot = findViewById(R.id.stateDot);
        tvTvName = findViewById(R.id.tvTvName);
        tvState = findViewById(R.id.tvState);
        tvScan = findViewById(R.id.tvScan);
        tvVolume = findViewById(R.id.tvVolume);
        tvList = findViewById(R.id.tvList);
        tabDpad = findViewById(R.id.tabDpad);
        tabTouch = findViewById(R.id.tabTouch);
        dpad = findViewById(R.id.dpad);
        touchpad = findViewById(R.id.touchpad);

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnPickTv).setOnClickListener(v -> showPicker(!pickerOpen));
        findViewById(R.id.btnRescan).setOnClickListener(v -> rescan());
        findViewById(R.id.btnManual).setOnClickListener(v -> manualIp());

        dpad.setOnKey(this::key);
        touchpad.setOnKey(this::key);
        tabDpad.setOnClickListener(v -> setTouchMode(false));
        tabTouch.setOnClickListener(v -> setTouchMode(true));

        tap(R.id.btnPower, KeyEvent.KEYCODE_POWER);
        tap(R.id.btnInput, KeyEvent.KEYCODE_TV_INPUT);
        tap(R.id.btnSettings, KeyEvent.KEYCODE_SETTINGS);
        tap(R.id.btnKeyBack, KeyEvent.KEYCODE_BACK);
        tap(R.id.btnHome, KeyEvent.KEYCODE_HOME);
        tap(R.id.btnMenu, KeyEvent.KEYCODE_MENU);
        tap(R.id.btnMute, KeyEvent.KEYCODE_VOLUME_MUTE);
        tap(R.id.btnPlay, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        tap(R.id.btnRewind, KeyEvent.KEYCODE_MEDIA_REWIND);
        tap(R.id.btnForward, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        hold(R.id.btnVolUp, KeyEvent.KEYCODE_VOLUME_UP);
        hold(R.id.btnVolDown, KeyEvent.KEYCODE_VOLUME_DOWN);
        hold(R.id.btnChUp, KeyEvent.KEYCODE_CHANNEL_UP);
        hold(R.id.btnChDown, KeyEvent.KEYCODE_CHANNEL_DOWN);
        app(R.id.appYoutube, "https://www.youtube.com");
        app(R.id.appNetflix, "https://www.netflix.com/title");
        app(R.id.appPrime, "https://app.primevideo.com");

        remote = new TvRemote(this, this);
        discovery = new TvDiscovery(this, this::onFound);

        SharedPreferences p = prefs();
        host = p.getString(K_HOST, null);
        name = p.getString(K_NAME, null);
        setTouchMode(p.getBoolean(K_TOUCH, false));
        if (name != null) tvTvName.setText(name);
        setState("Belum terhubung", R.color.text_muted);
        showPicker(host == null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        if (host != null && pairing == null && !remote.isConnected()) remote.connect(host);
        if (pickerOpen) startScan();
    }

    @Override
    protected void onStop() {
        visible = false;
        h.removeCallbacks(retry);
        h.removeCallbacks(scanHint);
        remote.disconnect();
        discovery.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        remote.release();
        closePairing();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (pickerOpen && host != null) showPicker(false);
        else super.onBackPressed();
    }

    // ------------------------------------------------------------ tombol HP

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                && remote.isConnected()) {
            remote.sendKey(keyCode, TvRemote.DIR_SHORT);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                && remote.isConnected()) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ------------------------------------------------------------ status koneksi

    @Override
    public void onState(int state, String detail) {
        h.removeCallbacks(retry);
        switch (state) {
            case TvRemote.STATE_CONNECTING:
                setState("Menghubungkan…", R.color.accent_light);
                break;
            case TvRemote.STATE_CONNECTED:
                setState("Terhubung", R.color.ok);
                if (pickerOpen) showPicker(false);
                break;
            case TvRemote.STATE_NEED_PAIRING:
                setState("Perlu pairing dengan TV", R.color.accent_orange);
                startPairing();
                break;
            case TvRemote.STATE_UNREACHABLE:
                setState(detail != null ? detail : "TV tidak terjangkau", R.color.danger);
                if (visible) h.postDelayed(retry, 4000);
                break;
            default:
                setState("Terputus · menyambung ulang…", R.color.danger);
                if (visible) h.postDelayed(retry, 1500);
                break;
        }
    }

    @Override
    public void onVolume(int level, int max, boolean muted) {
        tvVolume.setText(muted ? "Volume TV · Bisu" : "Volume TV · " + level + (max > 0 ? " / " + max : ""));
        ((ImageView) findViewById(R.id.btnMute)).setImageTintList(ColorStateList.valueOf(
                getColor(muted ? R.color.danger : R.color.text_secondary)));
    }

    @Override
    public void onPower(boolean on) {
        if (remote.isConnected()) {
            setState(on ? "Terhubung · TV menyala" : "Terhubung · TV standby", on ? R.color.ok : R.color.accent_orange);
        }
    }

    private void setState(String text, int colorRes) {
        tvState.setText(text);
        stateDot.setBackgroundTintList(ColorStateList.valueOf(getColor(colorRes)));
    }

    // ------------------------------------------------------------ kirim tombol

    private void key(int code, int direction) {
        if (!remote.isConnected()) {
            long now = SystemClock.uptimeMillis();
            if (now - lastToast > 2000) {
                lastToast = now;
                Toast.makeText(this, host == null ? "Pilih TV dulu" : "Belum terhubung ke TV",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        remote.sendKey(code, direction);
    }

    private void tap(int id, int code) {
        findViewById(id).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            key(code, TvRemote.DIR_SHORT);
        });
    }

    /** Tahan = berulang (untuk volume & channel). */
    @SuppressLint("ClickableViewAccessibility")
    private void hold(int id, int code) {
        View v = findViewById(id);
        Runnable[] rep = new Runnable[1];
        rep[0] = () -> {
            key(code, TvRemote.DIR_SHORT);
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            v.postDelayed(rep[0], 140);
        };
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    key(code, TvRemote.DIR_SHORT);
                    view.postDelayed(rep[0], 450);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    view.removeCallbacks(rep[0]);
                    return true;
                default:
                    return true;
            }
        });
    }

    private void app(int id, String link) {
        findViewById(id).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (!remote.isConnected()) {
                key(0, 0);
                return;
            }
            remote.launchApp(link);
        });
    }

    private void setTouchMode(boolean touch) {
        dpad.setVisibility(touch ? View.GONE : View.VISIBLE);
        touchpad.setVisibility(touch ? View.VISIBLE : View.GONE);
        styleTab(tabDpad, !touch);
        styleTab(tabTouch, touch);
        prefs().edit().putBoolean(K_TOUCH, touch).apply();
    }

    private void styleTab(TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.bg_tab_selected : 0);
        int c = getColor(on ? R.color.text_primary : R.color.text_secondary);
        t.setTextColor(c);
        t.setCompoundDrawableTintList(ColorStateList.valueOf(c));
    }

    // ------------------------------------------------------------ pilih TV

    private void showPicker(boolean show) {
        pickerOpen = show;
        connectPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        remotePanel.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) {
            rebuildList();
            if (visible) startScan();
        } else {
            discovery.stop();
            h.removeCallbacks(scanHint);
        }
    }

    private void startScan() {
        tvScan.setText(found.isEmpty() ? "Mencari Android TV di Wi-Fi yang sama…" : found.size() + " TV ditemukan");
        discovery.start();
        h.removeCallbacks(scanHint);
        h.postDelayed(scanHint, 9000);
    }

    private void rescan() {
        discovery.stop();
        found.clear();
        rebuildList();
        startScan();
    }

    private void onFound(String tvName, String tvHost) {
        found.put(tvHost, tvName);
        tvScan.setText(found.size() + " TV ditemukan");
        rebuildList();
    }

    private void rebuildList() {
        tvList.removeAllViews();
        Map<String, String> all = new LinkedHashMap<>();
        if (host != null) all.put(host, name != null ? name : host);
        all.putAll(found);
        for (Map.Entry<String, String> en : all.entrySet()) {
            tvList.addView(row(en.getValue(), en.getKey(), en.getKey().equals(host)));
        }
    }

    private View row(String tvName, String tvHost, boolean current) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.bg_row_click);
        int pad = dp(12);
        r.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);

        FrameLayout circle = new FrameLayout(this);
        circle.setBackgroundResource(R.drawable.bg_icon_circle);
        circle.setBackgroundTintList(ColorStateList.valueOf(0x2622D3FF));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_tv);
        ic.setImageTintList(ColorStateList.valueOf(getColor(R.color.accent_cyan)));
        circle.addView(ic, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        r.addView(circle, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
        tl.setMarginStart(dp(14));
        TextView t1 = new TextView(this);
        t1.setText(tvName);
        t1.setTextColor(getColor(R.color.text_primary));
        t1.setTextSize(15);
        t1.setSingleLine(true);
        TextView t2 = new TextView(this);
        t2.setText(tvHost);
        t2.setTextColor(getColor(R.color.text_secondary));
        t2.setTextSize(12);
        texts.addView(t1);
        texts.addView(t2);
        r.addView(texts, tl);

        if (current) {
            TextView tag = new TextView(this);
            tag.setText(remote.isConnected() ? "Terhubung" : "Terpilih");
            tag.setTextColor(getColor(R.color.ok));
            tag.setTextSize(12);
            r.addView(tag);
        }
        r.setOnClickListener(v -> select(tvHost, tvName));
        return r;
    }

    private void select(String tvHost, String tvName) {
        boolean same = tvHost.equals(host);
        host = tvHost;
        name = tvName;
        prefs().edit().putString(K_HOST, host).putString(K_NAME, name).apply();
        tvTvName.setText(name);
        showPicker(false);
        if (!same || !remote.isConnected()) {
            closePairing();
            remote.connect(host);
        }
    }

    private void manualIp() {
        EditText et = input("Contoh: 192.168.1.20", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setFilters(new InputFilter[]{(src, s, e, d, ds, de) -> {
            for (int i = s; i < e; i++) {
                char ch = src.charAt(i);
                if (!Character.isDigit(ch) && ch != '.') return "";
            }
            return null;
        }});
        if (host != null) et.setText(host);
        AlertDialog d = dialog()
                .setTitle("Alamat IP TV")
                .setMessage("Lihat di TV: Pengaturan › Jaringan & Internet › Wi-Fi yang tersambung.")
                .setView(wrap(et))
                .setPositiveButton("Sambungkan", null)
                .setNegativeButton("Batal", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ip = et.getText().toString().trim();
            if (!ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
                et.setError("Format IP tidak valid");
                return;
            }
            d.dismiss();
            select(ip, found.containsKey(ip) ? found.get(ip) : "Android TV");
        }));
        d.show();
        showKeyboard(et);
    }

    // ------------------------------------------------------------ pairing

    private void startPairing() {
        if (pairing != null || host == null) return;
        final String target = host;
        setState("Meminta kode pairing ke TV…", R.color.accent_orange);
        TvPairing p = new TvPairing(this, target);
        pairing = p;
        new Thread(() -> {
            try {
                p.start();
                runOnUiThread(() -> {
                    if (pairing == p && !isFinishing()) askCode(p);
                    else p.close();
                });
            } catch (Exception e) {
                p.close();
                runOnUiThread(() -> {
                    if (pairing == p) pairing = null;
                    if (isFinishing()) return;
                    setState("Pairing gagal", R.color.danger);
                    dialog().setTitle("Pairing gagal")
                            .setMessage("TV tidak merespons permintaan pairing (" + e.getMessage()
                                    + ").\nPastikan TV menyala, lalu coba lagi.")
                            .setPositiveButton("Coba lagi", (x, y) -> remote.connect(target))
                            .setNegativeButton("Tutup", null)
                            .show();
                });
            }
        }, "aldef-tv-pair").start();
    }

    private void askCode(TvPairing p) {
        setState("Masukkan kode yang tampil di TV", R.color.accent_orange);
        EditText et = input("Kode 6 karakter", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(6)});
        et.setGravity(Gravity.CENTER);
        et.setTextSize(24);
        et.setLetterSpacing(0.35f);
        AlertDialog d = dialog()
                .setTitle("Pairing dengan TV")
                .setMessage("Kode 6 karakter sekarang tampil di layar TV. Ketik kode tersebut di bawah ini.")
                .setView(wrap(et))
                .setCancelable(false)
                .setPositiveButton("Sambungkan", null)
                .setNegativeButton("Batal", (x, y) -> {
                    closePairing();
                    setState("Pairing dibatalkan", R.color.text_muted);
                })
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = et.getText().toString().trim();
            if (code.length() != 6) {
                et.setError("Kode harus 6 karakter");
                return;
            }
            v.setEnabled(false);
            new Thread(() -> {
                try {
                    boolean ok = p.finish(code);
                    runOnUiThread(() -> {
                        v.setEnabled(true);
                        if (!ok) {
                            et.setError("Kode tidak cocok. Periksa lagi layar TV.");
                            return;
                        }
                        d.dismiss();
                        closePairing();
                        Toast.makeText(this, "Berhasil dipasangkan dengan TV", Toast.LENGTH_SHORT).show();
                        if (host != null) remote.connect(host);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        d.dismiss();
                        closePairing();
                        setState("Pairing gagal: " + e.getMessage(), R.color.danger);
                        Toast.makeText(this, "Pairing gagal, ketuk ikon TV untuk mencoba lagi",
                                Toast.LENGTH_LONG).show();
                    });
                }
            }, "aldef-tv-secret").start();
        }));
        d.show();
        showKeyboard(et);
    }

    private void closePairing() {
        TvPairing p = pairing;
        pairing = null;
        if (p != null) new Thread(p::close).start();
    }

    // ------------------------------------------------------------ util

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, R.style.Theme_AldefPower_Dialog);
    }

    private EditText input(String hint, int type) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(type);
        et.setSingleLine(true);
        et.setTextColor(getColor(R.color.text_primary));
        et.setHintTextColor(getColor(R.color.text_muted));
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(dp(16), dp(14), dp(16), dp(14));
        return et;
    }

    private View wrap(View v) {
        FrameLayout f = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(22), dp(8), dp(22), dp(4));
        f.addView(v, lp);
        return f;
    }

    private void showKeyboard(View v) {
        v.requestFocus();
        v.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
