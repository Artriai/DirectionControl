package app.directioncontrol.utils;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public class ViewUtils {

    public static List<View> getAllChildViews(View view) {
        List<View> allChildViews = new ArrayList<>();
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0, size = viewGroup.getChildCount(); i < size; i++) {
                View viewChild = viewGroup.getChildAt(i);
                allChildViews.add(viewChild);
                if (viewChild instanceof ViewGroup) {
                    allChildViews.addAll(getAllChildViews(viewChild));
                }
            }
        }
        return allChildViews;
    }

    public static int dp(Context context, float dpValue) {
        if (dpValue == 0) {
            return 0;
        }
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    private ViewUtils() {
        throw new IllegalStateException();
    }
}
