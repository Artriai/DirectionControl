package app.directioncontrol.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Space;

import app.directioncontrol.BuildConfig;
import app.directioncontrol.MainActivity;
import app.directioncontrol.R;
import app.directioncontrol.preference.PreferenceManager;
import app.directioncontrol.utils.DirectionControlController;
import app.directioncontrol.utils.OrientationResolver;
import app.directioncontrol.utils.PermissionUtils;
import app.directioncontrol.utils.SimpleLog;

import androidx.annotation.RequiresApi;

public class DirectionControlService extends Service {

    private static final String TAG = DirectionControlService.class.getSimpleName();

    private static final String NOTIFICATION_ID = BuildConfig.APPLICATION_ID + ".notification";
    private static final int FLOATING_ARROW_REFRESH_DELAY_SHORT_MS = 120;
    private static final int FLOATING_ARROW_REFRESH_DELAY_LONG_MS = 300;

    public static final String ACTION_SET_ORIENTATION = BuildConfig.APPLICATION_ID + ".action.SET_ORIENTATION";
    public static final String KEY_ORIENTATION = "orientation";
    public static final String KEY_RESET_FLOATING_POSITION = "reset_floating_position";

    private static volatile boolean sServiceRunning;
    private static volatile boolean sFloatingWindowActive;

    private Space holderView = null;
    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable floatingArrowRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateFloatingArrow();
        }
    };

    private FloatingArrowWindow floatingArrowWindow = null;
    private OrientationMonitor orientationMonitor = null;
    private int detectedDegrees = 0;
    private int detectedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(Intent.ACTION_SCREEN_OFF.equals(action)){
                removeOrientationLayout();
                removeFloatingWindow();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                setSystemOrientation(currentOrientation);
                updateFloatingWindow();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sServiceRunning = true;
        orientationMonitor = new OrientationMonitor(this, mainHandler, new OrientationMonitor.Listener() {
            @Override
            public void onDetectedOrientationChanged(int degrees, int orientation) {
                applyDetectedOrientation(degrees, orientation);
            }

            @Override
            public void onDisplayChanged() {
                handleDisplayChanged();
            }
        });
        floatingArrowWindow = new FloatingArrowWindow(this, mainHandler, new FloatingArrowWindow.Callback() {
            @Override
            public void onArrowClicked() {
                lockToDetectedOrientation();
            }
        });
        if(Build.VERSION.SDK_INT >= 26) {
            setForeground();
        }
        IntentFilter screenReceiverFilter = new IntentFilter();
        screenReceiverFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenReceiverFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, screenReceiverFilter);
        SimpleLog.d(TAG, "register service receiver");
        updateFloatingWindow();
    }

    @Override
    public void onDestroy() {
        SimpleLog.d(TAG, "unregister service receiver");
        unregisterReceiver(screenReceiver);
        removeOrientationLayout();
        removeFloatingWindow();
        sServiceRunning = false;
        setFloatingWindowActive(false);
        super.onDestroy();
    }

    public static boolean isFloatingWindowActive() {
        return sServiceRunning && sFloatingWindowActive;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        boolean resetFloatingPosition = intent != null && intent.getBooleanExtra(KEY_RESET_FLOATING_POSITION, false);
        if (ACTION_SET_ORIENTATION.equals(action)) {
            int orientation = OrientationResolver.normalizeLockOrientation(
                    intent.getIntExtra(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
            if (orientation != currentOrientation) {
                setSystemOrientation(orientation);
            }
        }
        updateFloatingWindow();
        if (resetFloatingPosition && floatingArrowWindow != null) {
            floatingArrowWindow.resetPosition();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        turnOffForAppClose();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(26)
    private void setForeground(){
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_ID,
                getString(R.string.running_notification_channel_name),
                NotificationManager.IMPORTANCE_MIN);
        manager.createNotificationChannel(channel);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, NOTIFICATION_ID)
                .setContentTitle(getString(R.string.running_notification_title))
                .setContentText(getString(R.string.running_notification_description))
                .setSmallIcon(R.drawable.ic_stat_orientation)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
    }

    @SuppressWarnings("deprecation")
    private void setSystemOrientation(int screenOrientation) {
        SimpleLog.d(TAG, "set system orientation: " + screenOrientation);

        if (ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED == screenOrientation) {
            removeOrientationLayout();
            currentOrientation = screenOrientation;
            scheduleFloatingArrowRefresh();
            return;
        }

        currentOrientation = screenOrientation;

        WindowManager windowManager = (WindowManager) this.getSystemService(Service.WINDOW_SERVICE);
        WindowManager.LayoutParams orientationLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.RGBA_8888);

        if (holderView == null) {
            holderView = new Space(this);
            holderView.setClickable(false);
            holderView.setFocusable(false);
            holderView.setFocusableInTouchMode(false);
            holderView.setLongClickable(false);

            windowManager.addView(holderView, orientationLayoutParams);
            holderView.setVisibility(View.GONE);
        }

        orientationLayoutParams.screenOrientation = screenOrientation;
        windowManager.updateViewLayout(holderView, orientationLayoutParams);
        holderView.setVisibility(View.VISIBLE);
        scheduleFloatingArrowRefresh();
    }

    private void removeOrientationLayout() {
        SimpleLog.d(TAG, "remove system orientation");
        if (holderView == null) return;
        WindowManager windowManager = (WindowManager) this.getSystemService(Service.WINDOW_SERVICE);
        if (windowManager != null) {
            try {
                windowManager.removeViewImmediate(holderView);
            } catch (Exception ignore) {
            }
        }
        holderView = null;
    }

    private void turnOffForAppClose() {
        PreferenceManager pm = PreferenceManager.getInstance(this);
        pm.setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        pm.setShowFloatingWindow(false);
        setSystemOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        updateFloatingWindow();
        DirectionControlController.requestTileRefresh(this);
        DirectionControlController.notifyStateChanged(this);
        stopSelf();
    }

    private void updateFloatingWindow() {
        PreferenceManager pm = PreferenceManager.getInstance(this);
        boolean showFloating = pm.getShowFloatingWindow();
        boolean hasPermission = PermissionUtils.isDrawOverlaysPermissionGranted(this);

        if (showFloating && hasPermission) {
            if (floatingArrowWindow == null || !floatingArrowWindow.show()) {
                setFloatingWindowActive(false);
                return;
            }
            setFloatingWindowActive(true);
            if (orientationMonitor != null) {
                orientationMonitor.start();
            }
            scheduleFloatingArrowRefresh();
        } else {
            removeFloatingWindow();
        }
    }

    private void updateFloatingArrow() {
        if (floatingArrowWindow == null) {
            return;
        }
        floatingArrowWindow.updateArrow(detectedDegrees, getDisplayRotationDegrees(),
                currentOrientation == detectedOrientation);
    }

    private void scheduleFloatingArrowRefresh() {
        updateFloatingArrow();
        mainHandler.removeCallbacks(floatingArrowRefreshRunnable);
        mainHandler.postDelayed(floatingArrowRefreshRunnable, FLOATING_ARROW_REFRESH_DELAY_SHORT_MS);
        mainHandler.postDelayed(floatingArrowRefreshRunnable, FLOATING_ARROW_REFRESH_DELAY_LONG_MS);
    }

    private void lockToDetectedOrientation() {
        int nextOrientation = currentOrientation == detectedOrientation
                ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                : detectedOrientation;
        SimpleLog.d(TAG, "toggle detected orientation lock: " + nextOrientation);
        DirectionControlController.setOrientation(this, nextOrientation);
        scheduleFloatingArrowRefresh();
    }

    private void handleDisplayChanged() {
        if (floatingArrowWindow != null) {
            floatingArrowWindow.handleDisplayChanged();
        }
        scheduleFloatingArrowRefresh();
    }

    private void applyDetectedOrientation(int degrees, int orientation) {
        if (detectedOrientation == orientation && detectedDegrees == degrees) {
            return;
        }

        detectedDegrees = degrees;
        detectedOrientation = orientation;
        updateFloatingArrow();
    }

    private int getDisplayRotationDegrees() {
        if (orientationMonitor == null) {
            return 0;
        }
        return orientationMonitor.getDisplayRotationDegrees();
    }

    private void removeFloatingWindow() {
        SimpleLog.d(TAG, "remove floating window");
        if (orientationMonitor != null) {
            orientationMonitor.stop();
        }
        mainHandler.removeCallbacks(floatingArrowRefreshRunnable);
        if (floatingArrowWindow != null) {
            floatingArrowWindow.remove();
        }
        setFloatingWindowActive(false);
    }

    private void setFloatingWindowActive(boolean active) {
        if (sFloatingWindowActive == active) {
            return;
        }
        sFloatingWindowActive = active;
        DirectionControlController.requestTileRefresh(this);
    }
}
