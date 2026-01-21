package com.emhillstudio.tizcoret;

import android.content.Context;
import android.util.AttributeSet;
import androidx.recyclerview.widget.RecyclerView;
import android.view.MotionEvent;

public class NonInterceptingRecyclerView extends RecyclerView {

    public NonInterceptingRecyclerView(Context context) {
        super(context);
    }

    public NonInterceptingRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NonInterceptingRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        // Never intercept touch events — always let children (EditTexts) handle them
        return false;
    }
}
