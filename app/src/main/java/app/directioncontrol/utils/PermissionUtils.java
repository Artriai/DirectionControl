package app.directioncontrol.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.provider.Settings;

import app.directioncontrol.BuildConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PermissionUtils {

    public static boolean isDrawOverlaysPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            return Settings.canDrawOverlays(context);
        }
        try {
            Class<?> contextClass = Class.forName("android.content.Context");
            Field declaredField = contextClass.getDeclaredField("APP_OPS_SERVICE");
            declaredField.setAccessible(true);
            Object obj = declaredField.get(contextClass);
            if (!(obj instanceof String)) {
                return false;
            }
            String str2 = (String) obj;
            obj = contextClass.getMethod("getSystemService", String.class).invoke(context, str2);
            Class<?> appOpsClass = Class.forName("android.app.AppOpsManager");
            Field declaredField2 = appOpsClass.getDeclaredField("MODE_ALLOWED");
            declaredField2.setAccessible(true);
            Method checkOp = appOpsClass.getMethod("checkOp", Integer.TYPE, Integer.TYPE, String.class);
            int result = (Integer) checkOp.invoke(obj, 24, Binder.getCallingUid(), context.getPackageName());
            return result == declaredField2.getInt(appOpsClass);
        } catch (Exception e) {
            return false;
        }
    }

    public static void requestDrawOverlaysPermission(Context context){
        if (Build.VERSION.SDK_INT < 23) return;
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignore) {
        }
    }

    private PermissionUtils() {
        throw new IllegalStateException();
    }
}
