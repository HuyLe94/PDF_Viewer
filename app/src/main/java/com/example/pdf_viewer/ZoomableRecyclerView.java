package com.example.pdf_viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.recyclerview.widget.RecyclerView;

public class ZoomableRecyclerView extends RecyclerView {

    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;  // Initial scale factor
    private float maxScaleFactor = 3.0f;  // Maximum zoom level
    private float minScaleFactor = 1.0f;  // Minimum zoom level, no zooming out beyond default

    private float pivotX, pivotY;  // To keep track of zoom pivot points (for panning)

    // Screen width for detecting middle 50%
    private int screenWidth;
    private int zoomAreaStart, zoomAreaEnd; // Boundaries of zoomable area (middle 50%)

    public ZoomableRecyclerView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

        // Get the screen width and calculate the zoom area bounds (middle 50%)
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        zoomAreaStart = screenWidth / 100 *35; // 25% from the left
        zoomAreaEnd = screenWidth / 100 *65; // 25% from the right
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Get the X position of the touch event
        float touchX = event.getX();

        // Check if the touch is within the middle 50% of the screen
        if (touchX >= zoomAreaStart && touchX <= zoomAreaEnd) {
            // If inside the zoom area, handle the zoom gesture
            scaleGestureDetector.onTouchEvent(event);
        }

        // Pass the touch event to the parent for other handling (e.g., scrolling)
        return super.onTouchEvent(event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();

        // Apply scale transformation at the pivot points (this enables panning)
        canvas.scale(scaleFactor, scaleFactor, pivotX, pivotY);

        super.dispatchDraw(canvas);
        canvas.restore();
    }

    // Custom scale gesture listener for handling pinch-to-zoom
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();

            // Restrict zoom to between minScaleFactor and maxScaleFactor
            scaleFactor = Math.max(minScaleFactor, Math.min(scaleFactor, maxScaleFactor));

            // Get pivot points for zooming
            pivotX = detector.getFocusX();
            pivotY = detector.getFocusY();

            invalidate();  // Redraw the view with the new scale factor
            return true;
        }
    }
}
