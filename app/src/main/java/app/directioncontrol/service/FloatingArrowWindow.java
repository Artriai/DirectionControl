package app.directioncontrol.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageView;

import app.directioncontrol.R;
import app.directioncontrol.preference.PreferenceManager;
import app.directioncontrol.utils.OrientationResolver;

class FloatingArrowWindow {

    private static final int DISPLAY_REPOSITION_DELAY_SHORT_MS = 120;
    private static final int DISPLAY_REPOSITION_DELAY_LONG_MS = 300;

    interface Callback {
        void onArrowClicked();
    }

    private final Context context;
    private final Handler handler;
    private final Callback callback;

    private View floatingView;
    private ImageView arrowView;
    private WindowManager.LayoutParams layoutParams;
    private int lastDisplayWidth;
    private int lastDisplayHeight;

    private final Runnable repositionRunnable = new Runnable() {
        @Override
        public void run() {
            repositionForDisplayChange();
        }
    };

    FloatingArrowWindow(Context context, Handler handler, Callback callback) {
        this.context = context;
        this.handler = handler;
        this.callback = callback;
    }

    @SuppressLint("InflateParams")
    @SuppressWarnings("deprecation")
    boolean show() {
        if (floatingView != null) {
            return true;
        }

        WindowManager windowManager = getWindowManager();
        if (windowManager == null) {
            return false;
        }

        floatingView = LayoutInflater.from(context).inflate(R.layout.floating_window, null);
        arrowView = (ImageView) floatingView.findViewById(R.id.btn_floating_arrow);
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        restoreInitialPixelPosition();
        bindTouch();

        try {
            windowManager.addView(floatingView, layoutParams);
        } catch (RuntimeException e) {
            clearWindowState();
            return false;
        }
        floatingView.post(new Runnable() {
            @Override
            public void run() {
                syncPositionFromPreference();
            }
        });
        return true;
    }

    void remove() {
        handler.removeCallbacks(repositionRunnable);
        if (floatingView == null) {
            return;
        }

        WindowManager windowManager = getWindowManager();
        if (windowManager != null) {
            try {
                windowManager.removeViewImmediate(floatingView);
            } catch (Exception ignore) {
            }
        }

        clearWindowState();
    }

    void updateArrow(int detectedDegrees, int displayRotationDegrees, boolean selected) {
        if (arrowView == null) {
            return;
        }

        arrowView.setRotation(OrientationResolver.arrowRotationFromDegrees(
                detectedDegrees, displayRotationDegrees));
        arrowView.setColorFilter(selected ? 0xFF2196F3 : 0xFFFFFFFF);
        arrowView.setContentDescription(context.getString(selected
                ? R.string.floating_arrow_unlock_desc
                : R.string.floating_arrow_desc));
    }

    void handleDisplayChanged() {
        repositionForDisplayChange();
        handler.removeCallbacks(repositionRunnable);
        handler.postDelayed(repositionRunnable, DISPLAY_REPOSITION_DELAY_SHORT_MS);
        handler.postDelayed(repositionRunnable, DISPLAY_REPOSITION_DELAY_LONG_MS);
    }

    void syncPositionFromPreference() {
        if (floatingView == null || layoutParams == null) {
            return;
        }

        PreferenceManager pm = PreferenceManager.getInstance(context);
        Point displaySize = getDisplaySize();
        int maxX = Math.max(0, displaySize.x - getFloatingViewWidth());
        int maxY = Math.max(0, displaySize.y - getFloatingViewHeight());

        if (pm.hasFloatingPositionRatio() || !pm.hasFloatingXAndY()) {
            layoutParams.x = Math.round(clampRatio(pm.getFloatingXRatio()) * maxX);
            layoutParams.y = Math.round(clampRatio(pm.getFloatingYRatio()) * maxY);
        } else {
            layoutParams.x = pm.getFloatingX();
            layoutParams.y = pm.getFloatingY();
        }

        lastDisplayWidth = displaySize.x;
        lastDisplayHeight = displaySize.y;
        snapToNearestHorizontalEdge();
        updateLayout();
        floatingView.post(new Runnable() {
            @Override
            public void run() {
                clampToDisplay();
                snapToNearestHorizontalEdge();
                updateLayout();
                savePosition();
            }
        });
    }

    void resetPosition() {
        PreferenceManager.getInstance(context).resetFloatingPosition();
        syncPositionFromPreference();
    }

    private void restoreInitialPixelPosition() {
        PreferenceManager pm = PreferenceManager.getInstance(context);
        Point displaySize = getDisplaySize();
        lastDisplayWidth = displaySize.x;
        lastDisplayHeight = displaySize.y;

        if (pm.hasFloatingPositionRatio() || !pm.hasFloatingXAndY()) {
            layoutParams.x = Math.round(clampRatio(pm.getFloatingXRatio()) * displaySize.x);
            layoutParams.y = Math.round(clampRatio(pm.getFloatingYRatio()) * displaySize.y);
        } else {
            layoutParams.x = pm.getFloatingX();
            layoutParams.y = pm.getFloatingY();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindTouch() {
        arrowView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onArrowClicked();
            }
        });
        arrowView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                resetPosition();
                return true;
            }
        });
        arrowView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean moved;
            private boolean longPressed;
            private final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    longPressed = floatingView != null && arrowView != null && !moved
                            && arrowView.performLongClick();
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (floatingView == null || layoutParams == null) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        longPressed = false;
                        handler.removeCallbacks(longPressRunnable);
                        handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (!moved && Math.hypot(deltaX, deltaY) > touchSlop) {
                            moved = true;
                            handler.removeCallbacks(longPressRunnable);
                        }
                        if (moved && !longPressed) {
                            Point displaySize = getDisplaySize();
                            int maxX = Math.max(0, displaySize.x - getFloatingViewWidth());
                            int maxY = Math.max(0, displaySize.y - getFloatingViewHeight());
                            layoutParams.x = clamp(initialX + deltaX, 0, maxX);
                            layoutParams.y = clamp(initialY + deltaY, 0, maxY);
                            updateLayout();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPressRunnable);
                        clampToDisplay();
                        if (moved) {
                            snapToNearestHorizontalEdge();
                            updateLayout();
                        }
                        savePosition();
                        if (!moved && !longPressed) {
                            v.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressRunnable);
                        clampToDisplay();
                        if (moved) {
                            snapToNearestHorizontalEdge();
                            updateLayout();
                        }
                        savePosition();
                        return true;
                }
                return false;
            }
        });
    }

    private void repositionForDisplayChange() {
        if (floatingView == null || layoutParams == null) {
            updateDisplaySizeBaseline();
            return;
        }

        Point newDisplaySize = getDisplaySize();
        if (newDisplaySize.x <= 0 || newDisplaySize.y <= 0) {
            return;
        }

        if (lastDisplayWidth <= 0 || lastDisplayHeight <= 0) {
            lastDisplayWidth = newDisplaySize.x;
            lastDisplayHeight = newDisplaySize.y;
            clampToDisplay();
            savePosition();
            return;
        }

        if (lastDisplayWidth == newDisplaySize.x && lastDisplayHeight == newDisplaySize.y) {
            clampToDisplay();
            savePosition();
            return;
        }

        int viewWidth = getFloatingViewWidth();
        int viewHeight = getFloatingViewHeight();
        int oldMaxX = Math.max(0, lastDisplayWidth - viewWidth);
        int oldMaxY = Math.max(0, lastDisplayHeight - viewHeight);
        int newMaxX = Math.max(0, newDisplaySize.x - viewWidth);
        int newMaxY = Math.max(0, newDisplaySize.y - viewHeight);

        float xRatio = oldMaxX > 0 ? clampRatio((float) layoutParams.x / oldMaxX) : 0f;
        float yRatio = oldMaxY > 0 ? clampRatio((float) layoutParams.y / oldMaxY) : 0f;

        layoutParams.x = Math.round(xRatio * newMaxX);
        layoutParams.y = Math.round(yRatio * newMaxY);
        lastDisplayWidth = newDisplaySize.x;
        lastDisplayHeight = newDisplaySize.y;
        snapToNearestHorizontalEdge();
        updateLayout();
        savePosition();
    }

    private void clampToDisplay() {
        if (floatingView == null || layoutParams == null) {
            return;
        }

        Point displaySize = getDisplaySize();
        if (displaySize.x <= 0 || displaySize.y <= 0) {
            return;
        }

        int maxX = Math.max(0, displaySize.x - getFloatingViewWidth());
        int maxY = Math.max(0, displaySize.y - getFloatingViewHeight());
        int clampedX = clamp(layoutParams.x, 0, maxX);
        int clampedY = clamp(layoutParams.y, 0, maxY);
        lastDisplayWidth = displaySize.x;
        lastDisplayHeight = displaySize.y;

        if (clampedX == layoutParams.x && clampedY == layoutParams.y) {
            return;
        }

        layoutParams.x = clampedX;
        layoutParams.y = clampedY;
        updateLayout();
    }

    private void snapToNearestHorizontalEdge() {
        if (floatingView == null || layoutParams == null) {
            return;
        }

        Point displaySize = getDisplaySize();
        if (displaySize.x <= 0 || displaySize.y <= 0) {
            return;
        }

        int maxX = Math.max(0, displaySize.x - getFloatingViewWidth());
        int distanceToLeft = Math.abs(layoutParams.x);
        int distanceToRight = Math.abs(maxX - layoutParams.x);
        layoutParams.x = distanceToLeft <= distanceToRight ? 0 : maxX;
    }

    private void savePosition() {
        if (layoutParams == null) {
            return;
        }

        PreferenceManager pm = PreferenceManager.getInstance(context);
        pm.setFloatingPosition(layoutParams.x, layoutParams.y);

        Point displaySize = getDisplaySize();
        int maxX = Math.max(0, displaySize.x - getFloatingViewWidth());
        int maxY = Math.max(0, displaySize.y - getFloatingViewHeight());
        pm.setFloatingPositionRatio(
                maxX > 0 ? clampRatio((float) layoutParams.x / maxX) : 0f,
                maxY > 0 ? clampRatio((float) layoutParams.y / maxY) : 0f);
    }

    private void updateDisplaySizeBaseline() {
        Point displaySize = getDisplaySize();
        if (displaySize.x <= 0 || displaySize.y <= 0) {
            return;
        }
        lastDisplayWidth = displaySize.x;
        lastDisplayHeight = displaySize.y;
    }

    private void updateLayout() {
        WindowManager windowManager = getWindowManager();
        if (windowManager == null || floatingView == null || layoutParams == null) {
            return;
        }
        try {
            windowManager.updateViewLayout(floatingView, layoutParams);
        } catch (Exception ignore) {
        }
    }

    private Point getDisplaySize() {
        Point point = new Point();
        WindowManager windowManager = getWindowManager();
        if (windowManager == null) {
            return point;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windowManager.getMaximumWindowMetrics();
            Rect bounds = metrics.getBounds();
            point.x = bounds.width();
            point.y = bounds.height();
            return point;
        }

        getLegacyDisplaySize(windowManager, point);
        return point;
    }

    @SuppressWarnings("deprecation")
    private void getLegacyDisplaySize(WindowManager windowManager, Point point) {
        windowManager.getDefaultDisplay().getSize(point);
    }

    private WindowManager getWindowManager() {
        return (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    private void clearWindowState() {
        floatingView = null;
        arrowView = null;
        layoutParams = null;
        lastDisplayWidth = 0;
        lastDisplayHeight = 0;
    }

    private int getFloatingViewWidth() {
        if (floatingView == null) {
            return 0;
        }
        int width = floatingView.getWidth();
        return width > 0 ? width : floatingView.getMeasuredWidth();
    }

    private int getFloatingViewHeight() {
        if (floatingView == null) {
            return 0;
        }
        int height = floatingView.getHeight();
        return height > 0 ? height : floatingView.getMeasuredHeight();
    }

    private float clampRatio(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
