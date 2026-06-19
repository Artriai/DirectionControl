package app.directioncontrol.utils;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.Surface;

public class OrientationResolver {

    private OrientationResolver() {
    }

    public static int roundToRightAngle(int degrees) {
        int normalizedDegrees = ((degrees % 360) + 360) % 360;
        return ((normalizedDegrees + 45) / 90 * 90) % 360;
    }

    public static int stabilizeRightAngle(int degrees, int currentRoundedDegrees, int hysteresisMarginDegrees) {
        int currentDegrees = roundToRightAngle(currentRoundedDegrees);
        int candidateDegrees = roundToRightAngle(degrees);
        if (candidateDegrees == currentDegrees) {
            return currentDegrees;
        }

        int margin = Math.max(0, Math.min(44, hysteresisMarginDegrees));
        int switchThreshold = 45 + margin;
        return angularDistance(degrees, currentDegrees) >= switchThreshold
                ? candidateDegrees
                : currentDegrees;
    }

    public static int orientationFromDegrees(int degrees, boolean naturalLandscape) {
        switch (roundToRightAngle(degrees)) {
            case 90:
                return naturalLandscape
                        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            case 180:
                return naturalLandscape
                        ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case 270:
                return naturalLandscape
                        ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case 0:
            default:
                return naturalLandscape
                        ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
    }

    public static int arrowRotationFromDegrees(int sensorDegrees, int displayRotationDegrees) {
        return roundToRightAngle(displayRotationDegrees - sensorDegrees);
    }

    public static int normalizeLockOrientation(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:
                return orientation;
            default:
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    public static boolean isNaturalOrientationLandscape(int displayRotation, int configurationOrientation) {
        return ((displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180)
                && configurationOrientation == Configuration.ORIENTATION_LANDSCAPE)
                || ((displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270)
                && configurationOrientation == Configuration.ORIENTATION_PORTRAIT);
    }

    private static int angularDistance(int fromDegrees, int toDegrees) {
        int from = ((fromDegrees % 360) + 360) % 360;
        int to = ((toDegrees % 360) + 360) % 360;
        int distance = Math.abs(from - to);
        return Math.min(distance, 360 - distance);
    }
}
