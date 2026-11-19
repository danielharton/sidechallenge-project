package com.danielharton.scamshieldcompanion;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/** Root of the warning overlay: swallows the Back key so the warning cannot be dismissed before the timer ends. */
public class BlockingFrameLayout extends FrameLayout {
    public BlockingFrameLayout(Context context) {
        super(context);
    }

    public BlockingFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(event);
    }
}
