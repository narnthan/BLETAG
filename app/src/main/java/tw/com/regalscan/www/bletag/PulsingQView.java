/**
 * Quuppa Android Tag Emulation Demo application.
 *
 * Copyright 2015 Quuppa Oy
 *
 * Disclaimer
 * THE SOURCE CODE, DOCUMENTATION AND SPECIFICATIONS ARE PROVIDED “AS IS”. ALL LIABILITIES, WARRANTIES AND CONDITIONS, EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION TO THOSE CONCERNING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT
 * OF THIRD PARTY INTELLECTUAL PROPERTY RIGHTS ARE HEREBY EXCLUDED.
 */
package tw.com.regalscan.www.bletag;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

/**
 * Custom view to render Quuppa Q character on it with an pulsing animation.
 */
public class PulsingQView extends View {
    private Paint paint = null;
    private Handler mHandler = null;
    private final int mSampleDurationTime = 25;
    private boolean pulsing = false;
    private int alpha = 255;
    private boolean dirUp = true;

    public PulsingQView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PulsingQView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#FFFFFF"));
        paint.setStrokeWidth(40);
        mHandler = new Handler();
    }

    private final Runnable mRunnable = new Runnable() {
        public void run() {
            PulsingQView.this.invalidate();
            if (pulsing)
                mHandler.postDelayed(mRunnable, mSampleDurationTime);
        }
    };

    public void setIsPulsing(boolean isPulsing) {
        this.pulsing = isPulsing;
        this.invalidate();
        if (pulsing)
            mHandler.postDelayed(mRunnable, mSampleDurationTime);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int sw = getWidth();
        int sh = getHeight();
        int cx = sw / 2;
        int cy = sh / 2;
        int logoDim = (int) (Math.min(sw, sh) * 0.5f / 2.0f);
        canvas.drawColor(Color.BLACK);  // clears the canvas

        Path path = new Path();
        path.addArc(cx - logoDim, cy - logoDim, cx + logoDim, cy + logoDim, 100, 350);
        path.moveTo(cx, cy + logoDim / 2);
        path.lineTo(cx, cy + logoDim * 1.5f);

        if (pulsing) {
            if (dirUp)
                alpha += 5;
            else
                alpha -= 5;
            if (alpha > 255) {
                dirUp = false;
                alpha = 255;
            } else if (alpha < 30)
                dirUp = true;
            paint.setAlpha(alpha);
        } else {
            paint.setShader(null);
            paint.setAlpha(255);
            paint.setColor(Color.WHITE);
        }
        canvas.drawPath(path, paint);
    }
}
