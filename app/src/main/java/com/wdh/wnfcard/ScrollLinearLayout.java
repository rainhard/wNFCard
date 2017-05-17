package com.wdh.wnfcard;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.Scroller;


public class ScrollLinearLayout extends LinearLayout {

    private boolean mScrolled = false;
    private float density = 0;
    Scroller mScroller = null;

    public ScrollLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScroller = new Scroller(context);
        density = context.getApplicationContext().getResources().getDisplayMetrics().density;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), 0);
            postInvalidate();
        }
    }

    public void beginScroll() {
        if (!mScrolled) {
            mScroller.startScroll(0, 0, dipToPx(80), 0, 500);
            mScrolled = true;
        } else {
            mScroller.startScroll(dipToPx(80), 0, -dipToPx(80), 0, 1000);
            mScrolled = false;
        }
        invalidate();
    }

    private int dipToPx(int dip) {
        return (int) (dip * density + 0.5f);
    }
}
