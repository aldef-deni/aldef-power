package com.aldef.power;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Tile "Kunci Layar" di panel notifikasi. */
public class LockTileService extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t != null) {
            t.setState(Tile.STATE_INACTIVE);
            t.updateTile();
        }
    }

    @Override
    public void onClick() {
        if (!RebootHelper.lockScreen(this)) {
            RestartTileService.launch(this, QuickActionActivity.ACTION_LOCK);
        }
    }
}
