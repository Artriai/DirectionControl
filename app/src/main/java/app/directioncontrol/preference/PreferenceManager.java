package app.directioncontrol.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

import app.directioncontrol.utils.OrientationResolver;

public class PreferenceManager {

    private static final String KEY_ORIENTATION = "orientation";
    private static final String KEY_SHOW_FLOATING_WINDOW = "show_floating_window";
    private static final String KEY_FLOATING_X = "floating_x";
    private static final String KEY_FLOATING_Y = "floating_y";
    private static final String KEY_FLOATING_X_RATIO = "floating_x_ratio";
    private static final String KEY_FLOATING_Y_RATIO = "floating_y_ratio";
    private static final String KEY_TILE_ADDED = "tile_added";
    private static final int DEFAULT_FLOATING_X = 100;
    private static final int DEFAULT_FLOATING_Y = 200;
    private static final float DEFAULT_FLOATING_X_RATIO = 1f;
    private static final float DEFAULT_FLOATING_Y_RATIO = 0.5f;

    private static PreferenceManager sManager;

    private final SharedPreferences preferences;

    private static final String PREFERENCES = "settings";

    public static PreferenceManager getInstance(Context context) {
        if (sManager == null) {
            sManager = new PreferenceManager(context.getApplicationContext());
        }
        return sManager;
    }

    private PreferenceManager(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private void putInt(String name, int value) {
        if (name == null) {
            return;
        }
        preferences.edit().putInt(name, value).apply();
    }

    public void setOrientation(int orientation) {
        putInt(KEY_ORIENTATION, OrientationResolver.normalizeLockOrientation(orientation));
    }

    public int getOrientation() {
        return OrientationResolver.normalizeLockOrientation(
                preferences.getInt(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
    }

    public void setShowFloatingWindow(boolean show) {
        preferences.edit().putBoolean(KEY_SHOW_FLOATING_WINDOW, show).apply();
    }

    public boolean getShowFloatingWindow() {
        return preferences.getBoolean(KEY_SHOW_FLOATING_WINDOW, false);
    }

    public void setFloatingPosition(int x, int y) {
        preferences.edit().putInt(KEY_FLOATING_X, x).putInt(KEY_FLOATING_Y, y).apply();
    }

    public void setFloatingPositionRatio(float xRatio, float yRatio) {
        preferences.edit()
                .putFloat(KEY_FLOATING_X_RATIO, xRatio)
                .putFloat(KEY_FLOATING_Y_RATIO, yRatio)
                .apply();
    }

    public void resetFloatingPosition() {
        preferences.edit()
                .putInt(KEY_FLOATING_X, DEFAULT_FLOATING_X)
                .putInt(KEY_FLOATING_Y, DEFAULT_FLOATING_Y)
                .putFloat(KEY_FLOATING_X_RATIO, DEFAULT_FLOATING_X_RATIO)
                .putFloat(KEY_FLOATING_Y_RATIO, DEFAULT_FLOATING_Y_RATIO)
                .apply();
    }

    public int getFloatingX() {
        return preferences.getInt(KEY_FLOATING_X, DEFAULT_FLOATING_X);
    }

    public int getFloatingY() {
        return preferences.getInt(KEY_FLOATING_Y, DEFAULT_FLOATING_Y);
    }

    public boolean hasFloatingPositionRatio() {
        return preferences.contains(KEY_FLOATING_X_RATIO)
                && preferences.contains(KEY_FLOATING_Y_RATIO);
    }

    public float getFloatingXRatio() {
        return preferences.getFloat(KEY_FLOATING_X_RATIO, 0f);
    }

    public float getFloatingYRatio() {
        return preferences.getFloat(KEY_FLOATING_Y_RATIO, 0f);
    }

    public void setTileAdded(boolean added) {
        preferences.edit().putBoolean(KEY_TILE_ADDED, added).apply();
    }

    public boolean isTileAdded() {
        return preferences.getBoolean(KEY_TILE_ADDED, false);
    }
}
