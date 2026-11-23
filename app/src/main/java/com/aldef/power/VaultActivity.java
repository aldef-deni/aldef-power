package com.aldef.power;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Satu layar dengan 2 tab (Aplikasi & Media), dipakai untuk 2 mode:
 * - MODE_MANAGE : pilih aplikasi/media untuk disembunyikan (footer "ALDEF TECH" 5x)
 * - MODE_VAULT  : Ruang Privat, buka / tampilkan kembali item tersembunyi (logo 5x)
 */
public class VaultActivity extends Activity {

    static final String EXTRA_MODE = "mode";
    static final int MODE_MANAGE = 0;
    static final int MODE_VAULT = 1;

    /** Cegah screenshot & sembunyikan isi di layar Recent. */
    static final boolean SECURE = true;

    private static final int TAB_APPS = 0;
    private static final int TAB_MEDIA = 1;
    private static final int REQ_STORAGE = 41;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<VaultItem> allItems = new ArrayList<>(); // hasil muat penuh
    private final List<VaultItem> items = new ArrayList<>();    // setelah cari / filter
    private final Set<String> selected = new LinkedHashSet<>();

    private static final int FILTER_ALL = 0;
    private static final int FILTER_PHOTO = 1;
    private static final int FILTER_VIDEO = 2;
    private String query = "";
    private int mediaFilter = FILTER_ALL;
    private Result lastResult;
    private EditText etSearch;
    private View searchBar, btnClear, chipRow;
    private TextView chipAll, chipPhoto, chipVideo;
    private ThumbLoader thumbs;
    private ItemAdapter adapter;

    private int mode;
    private int tab = TAB_APPS;
    private boolean selectMode;
    private boolean busy;
    private int loadToken;

    private GridView grid;
    private View progress, emptyView, actionBar, btnMore;
    private TextView tvSubtitle, tabApps, tabMedia, tvAction, tvEmptyTitle, tvEmptyMsg, btnEmptyAction;
    private ImageView ivEmptyIcon, ivAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SECURE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_vault);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_MANAGE);
        thumbs = new ThumbLoader(this);

        grid = findViewById(R.id.grid);
        progress = findViewById(R.id.progress);
        emptyView = findViewById(R.id.emptyView);
        actionBar = findViewById(R.id.actionBar);
        btnMore = findViewById(R.id.btnMore);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tabApps = findViewById(R.id.tabApps);
        tabMedia = findViewById(R.id.tabMedia);
        tvAction = findViewById(R.id.tvAction);
        ivAction = findViewById(R.id.ivAction);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyMsg = findViewById(R.id.tvEmptyMsg);
        ivEmptyIcon = findViewById(R.id.ivEmptyIcon);
        btnEmptyAction = findViewById(R.id.btnEmptyAction);

        ((TextView) findViewById(R.id.tvTitle)).setText(
                mode == MODE_MANAGE ? "Sembunyikan" : "Ruang Privat");
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        btnMore.setVisibility(mode == MODE_VAULT ? View.VISIBLE : View.GONE);
        btnMore.setOnClickListener(v -> showMoreMenu());
        tabApps.setOnClickListener(v -> switchTab(TAB_APPS));
        tabMedia.setOnClickListener(v -> switchTab(TAB_MEDIA));
        actionBar.setOnClickListener(v -> onAction());

        adapter = new ItemAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onItemClick(items.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> onItemLongClick(items.get(pos)));
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state == SCROLL_STATE_TOUCH_SCROLL) hideKeyboard();
            }

            @Override
            public void onScroll(AbsListView v, int first, int visible, int total) {
            }
        });

        setupSearch();
        applyTabStyle();
    }

    // ------------------------------------------------------------ cari & filter

    private void setupSearch() {
        searchBar = findViewById(R.id.searchBar);
        etSearch = findViewById(R.id.etSearch);
        btnClear = findViewById(R.id.btnClear);
        chipRow = findViewById(R.id.chipRow);
        chipAll = findViewById(R.id.chipAll);
        chipPhoto = findViewById(R.id.chipPhoto);
        chipVideo = findViewById(R.id.chipVideo);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilter();
            }
        });
        etSearch.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        btnClear.setOnClickListener(v -> etSearch.setText(""));
        etSearch.setOnFocusChangeListener((v, focused) -> searchBar.setSelected(focused));

        chipAll.setOnClickListener(v -> setMediaFilter(FILTER_ALL));
        chipPhoto.setOnClickListener(v -> setMediaFilter(FILTER_PHOTO));
        chipVideo.setOnClickListener(v -> setMediaFilter(FILTER_VIDEO));
        styleChips();
    }

    private void setMediaFilter(int f) {
        if (mediaFilter == f) return;
        mediaFilter = f;
        styleChips();
        applyFilter();
    }

    private void styleChips() {
        styleChip(chipAll, mediaFilter == FILTER_ALL);
        styleChip(chipPhoto, mediaFilter == FILTER_PHOTO);
        styleChip(chipVideo, mediaFilter == FILTER_VIDEO);
    }

    private void styleChip(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        tv.setTextColor(getColor(active ? R.color.text_primary : R.color.text_secondary));
    }

    private boolean isFiltering() {
        return !query.isEmpty() || (tab == TAB_MEDIA && mediaFilter != FILTER_ALL);
    }

    /** Terapkan kata kunci & filter jenis media ke allItems -> items. */
    private void applyFilter() {
        items.clear();
        String q = query.toLowerCase(Locale.ROOT);
        for (VaultItem it : allItems) {
            if (!it.app && mediaFilter == FILTER_PHOTO && it.video) continue;
            if (!it.app && mediaFilter == FILTER_VIDEO && !it.video) continue;
            if (!q.isEmpty()
                    && !it.label.toLowerCase(Locale.ROOT).contains(q)
                    && !(it.app && it.key.toLowerCase(Locale.ROOT).contains(q))) continue;
            items.add(it);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
        updateChrome();
    }

    private void updateEmptyState() {
        if (lastResult == null) return;
        boolean hasData = !allItems.isEmpty();
        searchBar.setVisibility(hasData ? View.VISIBLE : View.GONE);
        chipRow.setVisibility(hasData && tab == TAB_MEDIA ? View.VISIBLE : View.GONE);

        if (!items.isEmpty()) {
            emptyView.setVisibility(View.GONE);
            return;
        }
        if (!hasData) {
            showEmpty(lastResult.icon, lastResult.title, lastResult.msg, lastResult.button, lastResult.action);
        } else {
            String what = mediaFilter == FILTER_PHOTO ? "foto" : mediaFilter == FILTER_VIDEO ? "video" : "item";
            showEmpty(R.drawable.ic_search, "Tidak ditemukan",
                    query.isEmpty() ? "Tidak ada " + what + " di sini."
                            : "Tidak ada " + what + " yang cocok dengan “" + query + "”.",
                    null, null);
        }
    }

    private void showEmpty(int icon, String title, String msg, String button, Runnable action) {
        if (emptyView.getVisibility() != View.VISIBLE) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setAlpha(0f);
            emptyView.animate().alpha(1f).setDuration(220).start();
        }
        ivEmptyIcon.setImageResource(icon);
        tvEmptyTitle.setText(title);
        tvEmptyMsg.setText(msg);
        btnEmptyAction.setVisibility(button != null ? View.VISIBLE : View.GONE);
        btnEmptyAction.setText(button);
        btnEmptyAction.setOnClickListener(v -> {
            if (action != null) action.run();
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        findViewById(R.id.content).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        thumbs.shutdown();
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (selectMode) {
            selectMode = false;
            selected.clear();
            adapter.notifyDataSetChanged();
            updateChrome();
            return;
        }
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQ_STORAGE) reload();
    }

    // ------------------------------------------------------------ tab

    private void switchTab(int t) {
        if (t == tab || busy) return;
        tab = t;
        query = "";
        mediaFilter = FILTER_ALL;
        etSearch.setText("");
        styleChips();
        hideKeyboard();
        applyTabStyle();
        reload();
    }

    private void applyTabStyle() {
        styleTab(tabApps, tab == TAB_APPS);
        styleTab(tabMedia, tab == TAB_MEDIA);
        grid.setNumColumns(tab == TAB_APPS ? 4 : 3);
        etSearch.setHint(tab == TAB_APPS ? "Cari aplikasi…" : "Cari foto & video…");
    }

    private void styleTab(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.bg_tab_selected : 0);
        tv.setTextColor(getColor(active ? R.color.text_primary : R.color.text_secondary));
    }

    // ------------------------------------------------------------ data

    private static final class Result {
        List<VaultItem> list = new ArrayList<>();
        int icon;
        String title, msg, button;
        Runnable action;
    }

    private void reload() {
        selected.clear();
        selectMode = false;
        items.clear();
        allItems.clear();
        lastResult = null;
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        updateChrome();

        final int token = ++loadToken;
        final int t = tab;
        worker.execute(() -> {
            if (mode == MODE_VAULT) AppHider.rehideTemporary(this);
            Result r = load(t);
            runOnUiThread(() -> {
                if (token == loadToken && !isFinishing()) show(r);
            });
        });
    }

    private Result load(int t) {
        Result r = new Result();
        if (t == TAB_APPS) {
            r.icon = R.drawable.ic_apps;
            if (mode == MODE_MANAGE) {
                if (!AppHider.isSupported(this)) {
                    r.icon = R.drawable.ic_shield;
                    r.title = "Tidak didukung";
                    r.msg = "HP ini tidak memiliki fitur Hide apps bawaan.";
                    return r;
                }
                r.list = AppHider.listVisibleApps(this);
                r.title = "Tidak ada aplikasi";
                r.msg = "Semua aplikasi sudah disembunyikan.";
            } else {
                r.list = AppHider.listHiddenApps(this);
                r.title = "Belum ada aplikasi tersembunyi";
                r.msg = "Aplikasi yang Anda sembunyikan akan muncul di sini.";
            }
        } else {
            r.icon = R.drawable.ic_image;
            if (!MediaVault.hasPermission(this)) {
                r.title = "Izinkan akses file";
                r.msg = "Dibutuhkan untuk menyembunyikan dan menampilkan foto & video.";
                r.button = "Beri Izin";
                r.action = () -> MediaVault.requestPermission(this, REQ_STORAGE);
                return r;
            }
            if (mode == MODE_MANAGE) {
                r.list = MediaVault.queryGallery(this);
                r.title = "Galeri kosong";
                r.msg = "Tidak ada foto atau video.";
            } else {
                r.list = MediaVault.listHidden();
                r.title = "Belum ada media tersembunyi";
                r.msg = "Foto & video yang Anda sembunyikan akan muncul di sini.";
            }
        }
        return r;
    }

    private void show(Result r) {
        progress.setVisibility(View.GONE);
        boolean realmeApps = tab == TAB_APPS && AppHider.usesRealme(this);
        View banner = findViewById(R.id.infoBanner);
        banner.setVisibility(realmeApps ? View.VISIBLE : View.GONE);
        View btnSync = findViewById(R.id.btnSync);
        btnSync.setVisibility(realmeApps && mode == MODE_VAULT ? View.VISIBLE : View.GONE);
        btnSync.setOnClickListener(v -> startRealmeSync());
        if (realmeApps) {
            ((TextView) findViewById(R.id.tvInfo)).setText(mode == MODE_MANAGE
                    ? "Tersinkron dengan Hide apps realme. Sakelar realme diatur otomatis."
                    : "Tersinkron dengan Hide apps realme. Ketuk aplikasi untuk membuka tanpa unhide.");
            if (mode == MODE_VAULT && RealmeSync.shouldAskFirstSync(this)) {
                RealmeSync.markAsked(this);
                dialog().setTitle("Sinkronkan dengan realme?")
                        .setMessage("Aplikasi yang sudah Anda sembunyikan lewat Hide apps realme akan "
                                + "dimasukkan ke Ruang Privat. Layar Hide apps akan terbuka sebentar.")
                        .setPositiveButton("Sinkronkan", (d, w) -> startRealmeSync())
                        .setNegativeButton("Nanti", null)
                        .show();
            }
        }
        lastResult = r;
        allItems.clear();
        allItems.addAll(r.list);
        grid.setAlpha(0f);
        grid.animate().alpha(1f).setDuration(220).start();
        applyFilter();
    }

    // ------------------------------------------------------------ interaksi

    private void onItemClick(VaultItem it) {
        if (busy) return;
        if (mode == MODE_MANAGE || selectMode) {
            toggle(it);
            return;
        }
        if (it.app) {
            AppHider.launch(this, it.key);
        } else {
            startActivity(new Intent(this, MediaViewerActivity.class)
                    .putExtra(MediaViewerActivity.EXTRA_PATH, it.path)
                    .putExtra(MediaViewerActivity.EXTRA_VIDEO, it.video)
                    .putExtra(MediaViewerActivity.EXTRA_NAME, it.label));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    private boolean onItemLongClick(VaultItem it) {
        if (mode != MODE_VAULT || busy) return false;
        selectMode = true;
        toggle(it);
        return true;
    }

    private void toggle(VaultItem it) {
        if (!selected.remove(it.key)) selected.add(it.key);
        adapter.notifyDataSetChanged();
        updateChrome();
    }

    private void updateChrome() {
        int n = selected.size();
        if (mode == MODE_MANAGE) {
            tvSubtitle.setText(n > 0 ? n + " dipilih"
                    : isFiltering() ? items.size() + " hasil"
                    : tab == TAB_APPS ? "Pilih aplikasi untuk disembunyikan"
                    : "Pilih foto & video untuk disembunyikan");
            tvAction.setText("Sembunyikan (" + n + ")");
            ivAction.setImageResource(R.drawable.ic_eye_off);
            setActionBarVisible(n > 0);
            actionBar.setAlpha(1f);
        } else {
            tvSubtitle.setText(selectMode ? n + " dipilih"
                    : isFiltering() ? items.size() + " hasil"
                    : "Ketuk buka · tahan untuk memilih");
            tvAction.setText("Tampilkan Kembali (" + n + ")");
            ivAction.setImageResource(R.drawable.ic_eye);
            setActionBarVisible(selectMode);
            actionBar.setAlpha(n > 0 ? 1f : 0.5f);
        }
    }

    private void setActionBarVisible(boolean show) {
        float off = 260 * getResources().getDisplayMetrics().density;
        if (show && actionBar.getVisibility() != View.VISIBLE) {
            actionBar.setVisibility(View.VISIBLE);
            actionBar.setTranslationY(off);
            actionBar.animate().translationY(0).setDuration(260).start();
        } else if (!show && actionBar.getVisibility() == View.VISIBLE) {
            actionBar.animate().translationY(off).setDuration(200)
                    .withEndAction(() -> actionBar.setVisibility(View.GONE)).start();
        }
    }

    private void onAction() {
        if (busy || selected.isEmpty()) return;
        List<VaultItem> chosen = new ArrayList<>();
        for (VaultItem it : allItems) if (selected.contains(it.key)) chosen.add(it);

        boolean hiding = mode == MODE_MANAGE;
        if (tab == TAB_APPS && AppHider.usesRealme(this)) {
            // Mode realme: catat di ALDEF, lalu buka "Hide apps" realme untuk menyalakan/mematikan sakelar
            StringBuilder names = new StringBuilder();
            for (VaultItem it : chosen) names.append("\n•  ").append(it.label);
            dialog().setTitle((hiding ? "Sembunyikan " : "Tampilkan kembali ") + chosen.size() + " aplikasi?")
                    .setMessage((hiding
                            ? "ALDEF akan menyalakan sakelarnya di Hide apps realme secara otomatis:"
                            : "ALDEF akan mematikan sakelarnya di Hide apps realme secara otomatis:") + names)
                    .setPositiveButton(hiding ? "Sembunyikan" : "Tampilkan", (d, w) -> runAction(chosen, true))
                    .setNegativeButton("Batal", null)
                    .show();
        } else if (hiding && tab == TAB_APPS) {
            dialog().setTitle("Sembunyikan " + chosen.size() + " aplikasi?")
                    .setMessage("Aplikasi akan hilang dari layar utama dan tidak berjalan di latar "
                            + "(tidak ada notifikasi). Buka kapan saja lewat Ruang Privat.")
                    .setPositiveButton("Sembunyikan", (d, w) -> runAction(chosen, false))
                    .setNegativeButton("Batal", null)
                    .show();
        } else {
            runAction(chosen, false);
        }
    }

    private void runAction(List<VaultItem> chosen, boolean openRealme) {
        busy = true;
        progress.setVisibility(View.VISIBLE);
        final boolean hiding = mode == MODE_MANAGE;
        final boolean apps = tab == TAB_APPS;
        worker.execute(() -> {
            int ok = 0;
            if (apps) {
                for (VaultItem it : chosen) {
                    if (hiding ? AppHider.hide(this, it.key) : AppHider.unhide(this, it.key)) ok++;
                }
            } else {
                ok = hiding ? MediaVault.hide(this, chosen) : MediaVault.unhide(this, chosen);
            }
            final int done = ok, total = chosen.size();
            runOnUiThread(() -> {
                busy = false;
                String msg = done + " item " + (hiding ? "disembunyikan" : "ditampilkan kembali");
                if (done < total) msg += " · " + (total - done) + " gagal";
                if (openRealme) {
                    Map<String, Boolean> ops = new LinkedHashMap<>();
                    for (VaultItem it : chosen) ops.put(it.key, hiding);
                    if (!RealmeSync.start(this, ops)) {
                        // Aksesibilitas mati: buka Hide apps, pengguna mengatur sakelar sendiri
                        RealmePrivacy.openHideApps(this);
                        Toast.makeText(this, (hiding ? "Nyalakan" : "Matikan")
                                + " sakelarnya secara manual di Hide apps", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
                reload();
            });
        });
    }

    private void startRealmeSync() {
        if (!RebootHelper.isAccessibilityOn()) {
            RebootHelper.needAccessibility(this);
            return;
        }
        if (!RealmeSync.start(this, new LinkedHashMap<>())) {
            Toast.makeText(this, "Hide apps realme tidak bisa dibuka", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMoreMenu() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (AppHider.usesRealme(this)) {
            labels.add("Sinkronkan dengan realme");
            actions.add(this::startRealmeSync);
            labels.add("Buka Hide apps realme");
            actions.add(() -> RealmePrivacy.openHideApps(this));
        }
        labels.add("Kosongkan daftar aplikasi");
        actions.add(() -> worker.execute(() -> {
            int n = AppHider.unhideAll(this);
            runOnUiThread(() -> {
                Toast.makeText(this, n + " aplikasi dikeluarkan dari Ruang Privat", Toast.LENGTH_SHORT).show();
                reload();
            });
        }));
        if (AppHider.isAvailable(this)) {
            labels.add("Lepas Mode Admin (sebelum uninstall)");
            actions.add(this::confirmReleaseAdmin);
        }
        dialog().setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run()).show();
    }

    @SuppressWarnings("deprecation")
    private void confirmReleaseAdmin() {
        dialog().setTitle("Lepas Mode Admin?")
                .setMessage("Semua aplikasi tersembunyi akan ditampilkan kembali. "
                        + "Setelah itu ALDEF POWER bisa di-uninstall.")
                .setPositiveButton("Lepas", (d, w) -> worker.execute(() -> {
                    AppHider.unhideAll(this);
                    try {
                        RebootHelper.dpm(this).clearDeviceOwnerApp(getPackageName());
                    } catch (Exception ignored) {
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Mode Admin dilepas", Toast.LENGTH_SHORT).show();
                        reload();
                    });
                }))
                .setNegativeButton("Batal", null)
                .show();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, R.style.Theme_AldefPower_Dialog);
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        return s >= 3600
                ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------ adapter

    private final class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return items.get(p).app ? 0 : 1; }

        @Override
        public View getView(int p, View cv, ViewGroup parent) {
            VaultItem it = items.get(p);
            boolean sel = selected.contains(it.key);
            if (it.app) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_app, parent, false);
                ((ImageView) cv.findViewById(R.id.icon)).setImageDrawable(it.icon);
                ((TextView) cv.findViewById(R.id.label)).setText(it.label);
                cv.findViewById(R.id.check).setVisibility(sel ? View.VISIBLE : View.GONE);
                cv.setBackgroundResource(sel ? R.drawable.bg_cell_selected : R.drawable.bg_cell);
            } else {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_media, parent, false);
                thumbs.load(cv.findViewById(R.id.thumb), it);
                cv.findViewById(R.id.videoBadge).setVisibility(it.video ? View.VISIBLE : View.GONE);
                ((TextView) cv.findViewById(R.id.duration)).setText(
                        it.durationMs > 0 ? formatDuration(it.durationMs) : "");
                cv.findViewById(R.id.selOverlay).setVisibility(sel ? View.VISIBLE : View.GONE);
                cv.findViewById(R.id.check).setVisibility(sel ? View.VISIBLE : View.GONE);
            }
            return cv;
        }
    }
}
