package app.directioncontrol.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.service.quicksettings.TileService;

import app.directioncontrol.BuildConfig;
import app.directioncontrol.preference.PreferenceManager;
import app.directioncontrol.service.DirectionControlService;
import app.directioncontrol.service.OrientationTileService;

public class DirectionControlController {

    public static final String ACTION_STATE_CHANGED = BuildConfig.APPLICATION_ID + ".action.STATE_CHANGED";

    private DirectionControlController() {
    }

    public static void setOrientation(Context context, int orientation) {
        Context appContext = context.getApplicationContext();
        PreferenceManager pm = PreferenceManager.getInstance(appContext);
        orientation = OrientationResolver.normalizeLockOrientation(orientation);
        pm.setOrientation(orientation);

        applyServiceState(appContext, orientation, pm.getShowFloatingWindow(), false);
    }

    public static void setFloatingWindowVisible(Context context, boolean visible) {
        Context appContext = context.getApplicationContext();
        PreferenceManager pm = PreferenceManager.getInstance(appContext);
        pm.setShowFloatingWindow(visible);
        if (!visible) {
            pm.setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        applyServiceState(appContext, visible ? pm.getOrientation() : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                visible, false);
    }

    public static void resetFloatingWindowPosition(Context context) {
        Context appContext = context.getApplicationContext();
        PreferenceManager pm = PreferenceManager.getInstance(appContext);
        pm.resetFloatingPosition();
        pm.setShowFloatingWindow(true);

        applyServiceState(appContext, pm.getOrientation(), true, true);
    }

    private static void applyServiceState(Context appContext, int orientation, boolean showFloating,
            boolean resetFloatingPosition) {
        orientation = OrientationResolver.normalizeLockOrientation(orientation);

        Intent intent = new Intent(appContext, DirectionControlService.class);
        intent.setAction(DirectionControlService.ACTION_SET_ORIENTATION);
        intent.putExtra(DirectionControlService.KEY_ORIENTATION, orientation);
        intent.putExtra(DirectionControlService.KEY_RESET_FLOATING_POSITION, resetFloatingPosition);

        boolean needService = (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                || (showFloating && PermissionUtils.isDrawOverlaysPermissionGranted(appContext));

        if (!needService) {
            appContext.stopService(intent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent);
        } else {
            appContext.startService(intent);
        }

        requestTileRefresh(appContext);
        notifyStateChanged(appContext);
    }

    public static void notifyStateChanged(Context context) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(appContext.getPackageName());
        appContext.sendBroadcast(intent);
    }

    public static void requestTileRefresh(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        TileService.requestListeningState(context.getApplicationContext(),
                new ComponentName(context, OrientationTileService.class));
    }
}
