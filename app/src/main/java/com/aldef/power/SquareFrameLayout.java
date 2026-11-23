package com.aldef.power;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** FrameLayout persegi dengan sudut membulat (mengikuti background). */
public class SquareFrameLayout extends FrameLayout {

    public SquareFrameLayout(Context c, AttributeSet a) {
        super(c, a);
        setClipToOutline(true);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, widthSpec);
    }
}
