/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

//This is a View that inherits from ImageView and supports double finger zooming, double tapping to enlarge, and restoring.
@SuppressLint("AppCompatCustomView")
public class AdvancedZoomableImageView extends ImageView {
    private Matrix matrix = new Matrix();
    private float scaleFactor = 1f;
    private float maxScale = 4f;
    private float minScale = 1f;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private PointF last = new PointF();
    private PointF start = new PointF();
    private int mode = NONE;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private float[] matrixValues = new float[9];

    public AdvancedZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);
        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureListener());

        //Ensure the image is centered at the beginning.
        post(new Runnable() {
            @Override
            public void run() {
                if (getDrawable() != null) {
                    centerImage();
                }
            }
        });
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    centerImage();
                }
            });
        }
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        post(new Runnable() {
            @Override
            public void run() {
                centerImage();
            }
        });
    }

    private void centerImage() {
        if (getDrawable() == null) return;

        //Reset matrix
        matrix.reset();

        //Get the size of the image and view
        int viewWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int viewHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        int drawableWidth = getDrawable().getIntrinsicWidth();
        int drawableHeight = getDrawable().getIntrinsicHeight();

        //Calculate the initial scaling factor (to fully display the image)
        float scaleX = (float) viewWidth / drawableWidth;
        float scaleY = (float) viewHeight / drawableHeight;
        float initialScale = Math.min(scaleX, scaleY);

        //Set initial zoom
        matrix.setScale(initialScale, initialScale);

        //Calculate the translation amount to center the image.
        float redundantXSpace = viewWidth - (initialScale * drawableWidth);
        float redundantYSpace = viewHeight - (initialScale * drawableHeight);
        float transX = redundantXSpace / 2;
        float transY = redundantYSpace / 2;

        matrix.postTranslate(transX, transY);
        scaleFactor = initialScale;

        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                last.set(curr);
                start.set(last);
                mode = DRAG;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    float deltaX = curr.x - last.x;
                    float deltaY = curr.y - last.y;
                    matrix.postTranslate(deltaX, deltaY);
                    fixTrans();
                    last.set(curr.x, curr.y);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mode = ZOOM;
                break;
        }

        setImageMatrix(matrix);
        return true;
    }

    private void fixTrans() {
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, getWidth(), getDrawable().getIntrinsicWidth() * scaleFactor);
        float fixTransY = getFixTrans(transY, getHeight(), getDrawable().getIntrinsicHeight() * scaleFactor);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans) return -trans + minTrans;
        if (trans > maxTrans) return -trans + maxTrans;
        return 0;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            float origScale = scaleFactor;
            scaleFactor *= mScaleFactor;
            scaleFactor = Math.max(minScale, Math.min(scaleFactor, maxScale));

            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            matrix.postScale(mScaleFactor, mScaleFactor, focusX, focusY);
            fixScaleTrans();
            setImageMatrix(matrix);
            return true;
        }
    }

    private void fixScaleTrans() {
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, getWidth(), getDrawable().getIntrinsicWidth() * scaleFactor);
        float fixTransY = getFixTrans(transY, getHeight(), getDrawable().getIntrinsicHeight() * scaleFactor);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (scaleFactor > minScale) {
                //Return to the initial state (centered display)
                centerImage();
            } else {
                //Zoomed to half of the maximum zoom level.
                float targetScale = maxScale / 2;
                float focusX = e.getX();
                float focusY = e.getY();

                matrix.postScale(targetScale / scaleFactor, targetScale / scaleFactor, focusX, focusY);
                scaleFactor = targetScale;
                fixScaleTrans();
                setImageMatrix(matrix);
            }
            return true;
        }
    }
}