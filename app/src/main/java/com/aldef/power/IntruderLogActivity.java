package com.aldef.power;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Galeri foto penyusup (hanya dari Ruang Privat asli). */
public class IntruderLogActivity extends Activity {

    private final List<File> files = new ArrayList<>();
    private ThumbLoader thumbs;
    private BaseAdapter adapter;
    private TextView tvSubtitle;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (VaultActivity.SECURE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_intruder_log);
        thumbs = new ThumbLoader(this);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        emptyView = findViewById(R.id.emptyView);
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> confirmClear());

        GridView grid = findViewById(R.id.grid);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return files.size(); }
            @Override public Object getItem(int p) { return files.get(p); }
            @Override public long getItemId(int p) { return p; }

            @Override
            public View getView(int p, View cv, ViewGroup parent) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_intruder, parent, false);
                File f = files.get(p);
                VaultItem it = new VaultItem(false, "i" + f.getName(), f.getName());
                it.path = f.getAbsolutePath();
                thumbs.load(cv.findViewById(R.id.thumb), it);
                ((TextView) cv.findViewById(R.id.tvReason)).setText(IntruderStore.reasonText(f));
                ((TextView) cv.findViewById(R.id.tvTime)).setText(IntruderStore.timeText(f.lastModified()));
                return cv;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> {
            File f = files.get(pos);
            startActivity(new Intent(this, MediaViewerActivity.class)
                    .putExtra(MediaViewerActivity.EXTRA_PATH, f.getAbsolutePath())
                    .putExtra(MediaViewerActivity.EXTRA_VIDEO, false)
                    .putExtra(MediaViewerActivity.EXTRA_NAME, IntruderStore.timeText(f.lastModified())));
        });
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            File f = files.get(pos);
            dialog().setTitle("Hapus foto ini?")
                    .setPositiveButton("Hapus", (d, w) -> {
                        f.delete();
                        refresh();
                    })
                    .setNegativeButton("Batal", null)
                    .show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        thumbs.shutdown();
        super.onDestroy();
    }

    private void refresh() {
        files.clear();
        files.addAll(IntruderStore.list(this));
        adapter.notifyDataSetChanged();
        tvSubtitle.setText(files.isEmpty() ? "Belum ada percobaan akses gagal"
                : files.size() + " percobaan tercatat · tahan untuk menghapus");
        emptyView.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnClearAll).setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmClear() {
        dialog().setTitle("Hapus semua foto penyusup?")
                .setPositiveButton("Hapus Semua", (d, w) -> {
                    IntruderStore.clear(this);
                    Toast.makeText(this, "Log penyusup dikosongkan", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, R.style.Theme_AldefPower_Dialog);
    }
}
