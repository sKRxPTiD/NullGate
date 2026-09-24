package org.nullprotocol.nullgate;

// Copyright © 2026 Null Protocol. All rights reserved.

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/** Circuit-trace section title used above the local audit log. */
public final class CircuitLabelView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CircuitLabelView(Context context) { super(context); }

    @Override protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        float cx = getWidth() / 2f;
        int green = Color.rgb(13, 119, 60);
        paint.setColor(green);
        paint.setStrokeWidth(dp(1.55f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        float leftInner = cx - dp(116);
        float rightInner = cx + dp(116);

        canvas.drawLine(0, cy + dp(6), dp(26), cy + dp(6), paint);
        canvas.drawLine(dp(26), cy + dp(6), dp(42), cy - dp(7), paint);
        canvas.drawLine(dp(42), cy - dp(7), leftInner - dp(6), cy - dp(7), paint);
        canvas.drawCircle(leftInner, cy - dp(7), dp(4), paint);
        canvas.drawLine(dp(35), cy + dp(13), dp(49), cy + dp(2), paint);
        canvas.drawLine(dp(49), cy + dp(2), leftInner + dp(22), cy + dp(2), paint);
        canvas.drawCircle(leftInner + dp(28), cy + dp(2), dp(3.5f), paint);

        canvas.drawLine(getWidth(), cy + dp(6), getWidth() - dp(26), cy + dp(6), paint);
        canvas.drawLine(getWidth() - dp(26), cy + dp(6), getWidth() - dp(42), cy - dp(7), paint);
        canvas.drawLine(getWidth() - dp(42), cy - dp(7), rightInner + dp(6), cy - dp(7), paint);
        canvas.drawCircle(rightInner, cy - dp(7), dp(4), paint);
        canvas.drawLine(getWidth() - dp(35), cy + dp(13), getWidth() - dp(49), cy + dp(2), paint);
        canvas.drawLine(getWidth() - dp(49), cy + dp(2), rightInner - dp(22), cy + dp(2), paint);
        canvas.drawCircle(rightInner - dp(28), cy + dp(2), dp(3.5f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(15));
        canvas.drawText("LOCAL AUDIT LOG", cx, cy + dp(5), paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
