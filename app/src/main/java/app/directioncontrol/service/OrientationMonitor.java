package app.directioncontrol.service;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import app.directioncontrol.utils.OrientationResolver;

class OrientationMonitor {

    private static final int ORIENTATION_HYSTERESIS_MARGIN_DEGREES = 15;
    private static final int NO_STABLE_DEGREES = -1;

    interface Listener {
        void onDetectedOrientationChanged(int degrees, int orientation);

        void onDisplayChanged();
    }

    private final Context context;
    private final Handler handler;
    private final Listener listener;

    private OrientationEventListener orientationEventListener;
    private DisplayManager.DisplayListener displayListener;
    private int stableDegrees = NO_STABLE_DEGREES;

    OrientationMonitor(Context context, Handler handler, Listener listener) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.listener = listener;
    }

    void start() {
        startOrientationDetection();
        startDisplayUpdates();
    }

    void stop() {
        stopOrientationDetection();
        stopDisplayUpdates();
    }

    @SuppressWarnings("deprecation")
    int getDisplayRotationDegrees() {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return 0;
        }

        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_0:
            default:
                return 0;
        }
    }

    private void startOrientationDetection() {
        if (orientationEventListener != null) {
            orientationEventListener.enable();
            return;
        }

        applyDetectedDegrees(getDisplayRotationDegrees());
        orientationEventListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    return;
                }
                applyDetectedDegrees(orientation);
            }
        };

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    private void stopOrientationDetection() {
        if (orientationEventListener == null) {
            return;
        }
        orientationEventListener.disable();
        orientationEventListener = null;
        stableDegrees = NO_STABLE_DEGREES;
    }

    private void startDisplayUpdates() {
        if (displayListener != null) {
            return;
        }

        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return;
        }

        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }

            @Override
            public void onDisplayChanged(int displayId) {
                listener.onDisplayChanged();
            }
        };
        displayManager.registerDisplayListener(displayListener, handler);
    }

    private void stopDisplayUpdates() {
        if (displayListener == null) {
            return;
        }

        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        displayListener = null;
    }

    private void applyDetectedDegrees(int degrees) {
        int roundedDegrees = stableDegrees == NO_STABLE_DEGREES
                ? OrientationResolver.roundToRightAngle(degrees)
                : OrientationResolver.stabilizeRightAngle(
                        degrees, stableDegrees, ORIENTATION_HYSTERESIS_MARGIN_DEGREES);
        stableDegrees = roundedDegrees;
        int nextOrientation = OrientationResolver.orientationFromDegrees(
                roundedDegrees, isNaturalOrientationLandscape());
        listener.onDetectedOrientationChanged(roundedDegrees, nextOrientation);
    }

    @SuppressWarnings("deprecation")
    private boolean isNaturalOrientationLandscape() {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        }

        int rotation = windowManager.getDefaultDisplay().getRotation();
        int orientation = context.getResources().getConfiguration().orientation;
        return OrientationResolver.isNaturalOrientationLandscape(rotation, orientation);
    }
}
