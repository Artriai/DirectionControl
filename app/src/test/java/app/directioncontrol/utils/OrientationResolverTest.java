package app.directioncontrol.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.Surface;

import org.junit.Test;

public class OrientationResolverTest {

    @Test
    public void roundToRightAngleNormalizesSensorDegrees() {
        assertEquals(0, OrientationResolver.roundToRightAngle(0));
        assertEquals(0, OrientationResolver.roundToRightAngle(44));
        assertEquals(90, OrientationResolver.roundToRightAngle(45));
        assertEquals(180, OrientationResolver.roundToRightAngle(181));
        assertEquals(270, OrientationResolver.roundToRightAngle(-90));
        assertEquals(0, OrientationResolver.roundToRightAngle(359));
    }

    @Test
    public void stabilizeRightAngleKeepsCurrentNearSwitchBoundary() {
        assertEquals(0, OrientationResolver.stabilizeRightAngle(46, 0, 15));
        assertEquals(0, OrientationResolver.stabilizeRightAngle(59, 0, 15));
        assertEquals(270, OrientationResolver.stabilizeRightAngle(329, 270, 15));
    }

    @Test
    public void stabilizeRightAngleSwitchesAfterHysteresisMargin() {
        assertEquals(90, OrientationResolver.stabilizeRightAngle(60, 0, 15));
        assertEquals(0, OrientationResolver.stabilizeRightAngle(330, 270, 15));
    }

    @Test
    public void orientationFromDegreesUsesPhoneNaturalPortraitMapping() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                OrientationResolver.orientationFromDegrees(0, false));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                OrientationResolver.orientationFromDegrees(90, false));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                OrientationResolver.orientationFromDegrees(180, false));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                OrientationResolver.orientationFromDegrees(270, false));
    }

    @Test
    public void orientationFromDegreesUsesTabletNaturalLandscapeMapping() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                OrientationResolver.orientationFromDegrees(0, true));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                OrientationResolver.orientationFromDegrees(90, true));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                OrientationResolver.orientationFromDegrees(180, true));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                OrientationResolver.orientationFromDegrees(270, true));
    }

    @Test
    public void arrowRotationIsRelativeToCurrentDisplayRotation() {
        assertEquals(0, OrientationResolver.arrowRotationFromDegrees(90, 90));
        assertEquals(90, OrientationResolver.arrowRotationFromDegrees(0, 90));
        assertEquals(270, OrientationResolver.arrowRotationFromDegrees(90, 0));
    }

    @Test
    public void normalizeLockOrientationKeepsOnlyArrowLockTargets() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                OrientationResolver.normalizeLockOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                OrientationResolver.normalizeLockOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                OrientationResolver.normalizeLockOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
    }

    @Test
    public void naturalOrientationLandscapeMatchesRotationAndConfiguration() {
        assertTrue(OrientationResolver.isNaturalOrientationLandscape(
                Surface.ROTATION_0, Configuration.ORIENTATION_LANDSCAPE));
        assertTrue(OrientationResolver.isNaturalOrientationLandscape(
                Surface.ROTATION_90, Configuration.ORIENTATION_PORTRAIT));
        assertFalse(OrientationResolver.isNaturalOrientationLandscape(
                Surface.ROTATION_0, Configuration.ORIENTATION_PORTRAIT));
    }
}
