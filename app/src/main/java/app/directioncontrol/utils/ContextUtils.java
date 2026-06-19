package app.directioncontrol.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;

public class ContextUtils {

    public static int getColorFromAttr(Context context, int attrColorRes) {
        TypedValue typedValue = new TypedValue();
        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[] { attrColorRes });
        int color = a.getColor(0, 0);
        a.recycle();
        return color;
    }

    private ContextUtils() {
        throw new IllegalStateException();
    }
}
