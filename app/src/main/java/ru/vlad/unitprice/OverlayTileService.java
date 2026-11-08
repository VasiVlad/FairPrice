package ru.vlad.unitprice;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Плитка в шторке быстрых настроек: включить/выключить чипы. */
public final class OverlayTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Prefs.setOverlayEnabled(this, !Prefs.overlayEnabled(this));
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(Prefs.overlayEnabled(this) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Цена за кг");
        tile.updateTile();
    }
}
