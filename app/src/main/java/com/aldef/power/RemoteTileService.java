package com.aldef.power;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Tile "Aldef TV" di panel notifikasi: langsung membuka remote. */
public class RemoteTileService extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t != null) {
            t.setState(Tile.STATE_INACTIVE);
            t.updateTile();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick() {
        Intent i = new Intent(this, RemoteActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (isLocked()) {
            unlockAndRun(() -> startActivityAndCollapse(i));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
