package app.directioncontrol.service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import app.directioncontrol.MainActivity;
import app.directioncontrol.R;
import app.directioncontrol.preference.PreferenceManager;
import app.directioncontrol.utils.DirectionControlController;
import app.directioncontrol.utils.PermissionUtils;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.N)
public class OrientationTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        PreferenceManager.getInstance(this).setTileAdded(true);
        updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        PreferenceManager.getInstance(this).setTileAdded(true);
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        PreferenceManager.getInstance(this).setTileAdded(false);
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
            openMainActivity();
            updateTile();
            return;
        }

        PreferenceManager preferenceManager = PreferenceManager.getInstance(this);
        boolean shouldHideFloating = DirectionControlService.isFloatingWindowActive();
        DirectionControlController.setFloatingWindowVisible(this, !shouldHideFloating);
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        boolean hasPermission = PermissionUtils.isDrawOverlaysPermissionGranted(this);
        PreferenceManager preferenceManager = PreferenceManager.getInstance(this);
        boolean showFloating = preferenceManager.getShowFloatingWindow();
        boolean locked = preferenceManager.getOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        tile.setLabel(getString(showFloating ? R.string.tile_floating_on : R.string.tile_floating_off));

        if (!hasPermission) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(getString(R.string.permission_required));
            }
        } else {
            tile.setState(showFloating ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle(getString(getTileSubtitleRes(showFloating, locked)));
            }
        }
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_orientation_arrow));
        tile.updateTile();
    }

    private int getTileSubtitleRes(boolean showFloating, boolean locked) {
        if (!showFloating) {
            return R.string.tile_click_to_show_floating;
        }
        return locked
                ? R.string.tile_locked_click_to_hide_floating
                : R.string.tile_unlocked_click_to_hide_floating;
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_REQUEST_OVERLAY_PERMISSION, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
