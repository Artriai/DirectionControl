package app.directioncontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;

import app.directioncontrol.service.OrientationTileService;
import app.directioncontrol.utils.DirectionControlController;
import app.directioncontrol.utils.SimpleLog;

public class StateChangeReceiver extends BroadcastReceiver {

    private static final String TAG = StateChangeReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DirectionControlController.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }

        SimpleLog.d(TAG, "refresh tile from state broadcast");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            boolean showFloating = intent.getBooleanExtra(
                    DirectionControlController.EXTRA_SHOW_FLOATING_WINDOW, false);
            int orientation = intent.getIntExtra(
                    DirectionControlController.EXTRA_ORIENTATION,
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            OrientationTileService.refreshListeningTile(showFloating, orientation);
        }
        DirectionControlController.requestTileRefresh(context);
    }
}
