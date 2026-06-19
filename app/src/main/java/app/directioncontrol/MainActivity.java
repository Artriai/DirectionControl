package app.directioncontrol;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import app.directioncontrol.preference.PreferenceManager;
import app.directioncontrol.service.OrientationTileService;
import app.directioncontrol.utils.ContextUtils;
import app.directioncontrol.utils.DirectionControlController;
import app.directioncontrol.utils.PermissionUtils;
import app.directioncontrol.utils.ViewUtils;

public class MainActivity extends Activity {

    public static final String EXTRA_REQUEST_OVERLAY_PERMISSION = "request_overlay_permission";

    private PreferenceManager preferenceManager;
    private boolean stateReceiverRegistered;
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSettingsViews();
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }

        setContentView(R.layout.main);
        preferenceManager = PreferenceManager.getInstance(this);

        applyWindowInsets();
        bindActions();

        int orientation = PermissionUtils.isDrawOverlaysPermissionGranted(this)
                ? preferenceManager.getOrientation()
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        DirectionControlController.setOrientation(this, orientation);

        if (getIntent().getBooleanExtra(EXTRA_REQUEST_OVERLAY_PERMISSION, false)
                && !PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
            requestOverlayPermission();
        }

        updateSettingsViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerStateReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSettingsViews();
    }

    @Override
    protected void onStop() {
        unregisterStateReceiver();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (!isChangingConfigurations()) {
            DirectionControlController.setOrientation(this, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        super.onDestroy();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStateReceiver() {
        if (stateReceiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter(DirectionControlController.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        stateReceiverRegistered = true;
    }

    private void unregisterStateReceiver() {
        if (!stateReceiverRegistered) {
            return;
        }

        unregisterReceiver(stateReceiver);
        stateReceiverRegistered = false;
    }

    @SuppressWarnings("deprecation")
    private void applyWindowInsets() {
        findViewById(R.id.ll_root).setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                top = systemBars.top;
                bottom = systemBars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            int designPaddingTop = ViewUtils.dp(MainActivity.this, 16);
            int designPaddingBottom = ViewUtils.dp(MainActivity.this, 16);
            view.setPadding(view.getPaddingLeft(), top + designPaddingTop,
                    view.getPaddingRight(), bottom + designPaddingBottom);
            return insets;
        });
    }

    private void bindActions() {
        bindClick(R.id.ll_setting_floating, v -> toggleFloatingWindow());
        bindClick(R.id.ll_action_unlock, v -> unlockOrientation());
        bindClick(R.id.ll_action_reset_position, v -> resetFloatingPosition());
        bindClick(R.id.ll_setting_tile, v -> requestAddTile());
    }

    private void bindClick(int viewId, View.OnClickListener listener) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    private void updateSettingsViews() {
        boolean hasOverlay = PermissionUtils.isDrawOverlaysPermissionGranted(this);
        boolean showFloating = preferenceManager.getShowFloatingWindow();
        int orientation = hasOverlay
                ? preferenceManager.getOrientation()
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

        Switch switchFloating = findViewById(R.id.switch_floating);
        if (switchFloating != null) {
            switchFloating.setChecked(hasOverlay && showFloating);
        }

        TextView statusTitle = findViewById(R.id.tv_status_title);
        TextView statusDetail = findViewById(R.id.tv_status_detail);
        if (statusTitle != null && statusDetail != null) {
            if (!hasOverlay) {
                statusTitle.setText(R.string.status_permission_required);
                statusDetail.setText(R.string.status_permission_detail);
            } else if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                statusTitle.setText(R.string.status_unlocked);
                statusDetail.setText(showFloating
                        ? R.string.tile_floating_on
                        : R.string.tile_floating_off);
            } else {
                statusTitle.setText(R.string.status_locked);
                statusDetail.setText(getString(R.string.status_locked_detail,
                        getOrientationLabel(orientation),
                        getString(showFloating ? R.string.tile_floating_on : R.string.tile_floating_off)));
            }
        }

        updateUnlockRow(hasOverlay && orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        updateTileRow();
    }

    private void updateUnlockRow(boolean enabled) {
        LinearLayout unlockRow = findViewById(R.id.ll_action_unlock);
        TextView unlockAction = findViewById(R.id.tv_unlock_action);
        if (unlockRow == null || unlockAction == null) {
            return;
        }

        unlockRow.setEnabled(enabled);
        unlockRow.setClickable(enabled);
        unlockRow.setAlpha(enabled ? 1f : 0.45f);
        unlockAction.setText(enabled
                ? R.string.setting_unlock_action
                : R.string.setting_unlocked_action);
    }

    private void updateTileRow() {
        LinearLayout tileRow = findViewById(R.id.ll_setting_tile);
        TextView tileAction = findViewById(R.id.tv_add_tile_action);
        if (tileRow == null || tileAction == null) {
            return;
        }

        boolean isAdded = preferenceManager.isTileAdded();
        if (isAdded) {
            tileAction.setText(R.string.setting_qs_tile_added);
            tileAction.setTextColor(ContextUtils.getColorFromAttr(this, android.R.attr.textColorSecondary));
            tileRow.setClickable(false);
            tileRow.setFocusable(false);
            tileRow.setBackground(null);
        } else {
            tileAction.setText(R.string.setting_qs_tile_add);
            tileAction.setTextColor(ContextUtils.getColorFromAttr(this, android.R.attr.colorAccent));
            tileRow.setClickable(true);
            tileRow.setFocusable(true);
            tileRow.setBackgroundResource(R.drawable.bg_setting_item);
        }
    }

    private void toggleFloatingWindow() {
        if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
            requestOverlayPermission();
            return;
        }

        DirectionControlController.setFloatingWindowVisible(this, !preferenceManager.getShowFloatingWindow());
        updateSettingsViews();
    }

    private void unlockOrientation() {
        DirectionControlController.setOrientation(this, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        updateSettingsViews();
    }

    private void resetFloatingPosition() {
        if (!PermissionUtils.isDrawOverlaysPermissionGranted(this)) {
            requestOverlayPermission();
            return;
        }

        DirectionControlController.resetFloatingWindowPosition(this);
        Toast.makeText(this, R.string.setting_reset_position_done, Toast.LENGTH_SHORT).show();
        updateSettingsViews();
    }

    private void requestAddTile() {
        if (preferenceManager.isTileAdded()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusBarManager statusBarManager = getSystemService(StatusBarManager.class);
            if (statusBarManager == null) {
                return;
            }

            ComponentName componentName = new ComponentName(this, OrientationTileService.class);
            Icon icon = Icon.createWithResource(this, R.drawable.ic_stat_orientation);
            statusBarManager.requestAddTileService(
                    componentName,
                    getString(R.string.quick_settings_tile_label),
                    icon,
                    getMainExecutor(),
                    result -> {
                        if (result == 1 || result == 2) {
                            preferenceManager.setTileAdded(true);
                            updateSettingsViews();
                        }
                    }
            );
        } else {
            Toast.makeText(this, R.string.setting_qs_tile_desc, Toast.LENGTH_LONG).show();
        }
    }

    private void requestOverlayPermission() {
        PermissionUtils.requestDrawOverlaysPermission(this);
        Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
    }

    private String getOrientationLabel(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                return getString(R.string.orientation_landscape);
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                return getString(R.string.orientation_reverse_landscape);
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                return getString(R.string.orientation_portrait);
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                return getString(R.string.orientation_reverse_portrait);
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:
            default:
                return getString(R.string.orientation_default);
        }
    }
}
