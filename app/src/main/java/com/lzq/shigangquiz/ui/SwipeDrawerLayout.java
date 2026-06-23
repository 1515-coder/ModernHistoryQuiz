package com.lzq.shigangquiz.ui;

import android.view.MotionEvent;
import android.view.VelocityTracker;

import androidx.core.view.GravityCompat;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.customview.widget.ViewDragHelper;

import java.lang.reflect.Field;
import java.util.Collections;

/**
 * DrawerLayout with a wider left-edge drag area.
 *
 * The drawer still uses DrawerLayout's native ViewDragHelper, so it follows
 * the finger continuously instead of waiting for a distance threshold and
 * then opening suddenly.
 *
 * The opening gesture may start within about 1.5 cm from the left edge.
 */
public final class SwipeDrawerLayout extends DrawerLayout {

    private static final float EDGE_WIDTH_CM = 1.5f;
    private static final float FAST_SWIPE_DP_PER_SECOND = 400f;

    private VelocityTracker velocityTracker;
    private boolean startedFromLeftEdge;

    public SwipeDrawerLayout(Context context) {
        super(context);
        initWideSwipeEdge();
    }

    public SwipeDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initWideSwipeEdge();
    }

    public SwipeDrawerLayout(
            Context context,
            AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        initWideSwipeEdge();
    }

    private void initWideSwipeEdge() {
        /*
         * DrawerLayout creates its ViewDragHelper during construction.
         * post() waits until the view has entered the UI queue before
         * widening the native left-edge detection area.
         */
        post(this::applyWideLeftEdge);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addOnLayoutChangeListener((
                    view,
                    left,
                    top,
                    right,
                    bottom,
                    oldLeft,
                    oldTop,
                    oldRight,
                    oldBottom
            ) -> applySystemGestureExclusion());
        }
    }

    private int edgeWidthPx() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int physicalPixels = Math.round(
                EDGE_WIDTH_CM / 2.54f * metrics.xdpi
        );

        /*
         * Some devices report inaccurate physical DPI values.
         * Keep the result in a practical range while remaining close to
         * 1.5 cm on normal phones.
         */
        int minimum = Math.round(72f * metrics.density);
        int maximum = Math.round(120f * metrics.density);

        return Math.max(minimum, Math.min(physicalPixels, maximum));
    }

    private void applyWideLeftEdge() {
        try {
            Field leftDraggerField =
                    DrawerLayout.class.getDeclaredField("mLeftDragger");
            leftDraggerField.setAccessible(true);

            Object draggerObject = leftDraggerField.get(this);
            if (!(draggerObject instanceof ViewDragHelper)) {
                return;
            }

            Field edgeSizeField =
                    ViewDragHelper.class.getDeclaredField("mEdgeSize");
            edgeSizeField.setAccessible(true);
            edgeSizeField.setInt(draggerObject, edgeWidthPx());
        } catch (ReflectiveOperationException ignored) {
            /*
             * If a future DrawerLayout implementation changes its private
             * fields, the layout safely falls back to the standard native
             * edge width. The menu button will still work.
             */
        }
    }

    private void applySystemGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || getWidth() <= 0
                || getHeight() <= 0) {
            return;
        }

        Rect leftEdge = new Rect(
                0,
                0,
                Math.min(edgeWidthPx(), getWidth()),
                getHeight()
        );

        setSystemGestureExclusionRects(
                Collections.singletonList(leftEdge)
        );
    }
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            if (velocityTracker != null) {
                velocityTracker.recycle();
            }

            velocityTracker = VelocityTracker.obtain();
            velocityTracker.addMovement(event);

            startedFromLeftEdge =
                    !isDrawerVisible(GravityCompat.START)
                            && event.getX() <= edgeWidthPx();

        } else if (velocityTracker != null) {
            velocityTracker.addMovement(event);

            if (action == MotionEvent.ACTION_UP) {
                velocityTracker.computeCurrentVelocity(1000);

                float velocityX = velocityTracker.getXVelocity();
                float velocityY = velocityTracker.getYVelocity();
                float density = getResources().getDisplayMetrics().density;

                float requiredVelocity =
                        FAST_SWIPE_DP_PER_SECOND * density;

                boolean fastSwipeRight =
                        startedFromLeftEdge
                                && velocityX >= requiredVelocity
                                && velocityX > Math.abs(velocityY) * 1.2f;

                boolean handled = super.dispatchTouchEvent(event);

                if (fastSwipeRight) {
                    post(() -> openDrawer(GravityCompat.START, true));
                }

                velocityTracker.recycle();
                velocityTracker = null;
                startedFromLeftEdge = false;

                return handled;
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                velocityTracker.recycle();
                velocityTracker = null;
                startedFromLeftEdge = false;
            }
        }

        return super.dispatchTouchEvent(event);
    }
}
