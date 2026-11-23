package com.aldef.power;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Tile "Restart" di panel notifikasi. */
public class RestartTileService extends TileService {

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
        launch(this, QuickActionActivity.ACTION_RESTART);
    }

    @SuppressWarnings("deprecation")
    static void launch(TileService s, String action) {
        Intent i = new Intent(s, QuickActionActivity.class)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            s.startActivityAndCollapse(PendingIntent.getActivity(s, action.hashCode(), i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            s.startActivityAndCollapse(i);
        }
    }
}
