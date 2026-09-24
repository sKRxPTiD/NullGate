package org.nullprotocol.nullgate;

// Copyright © 2026 Null Protocol. All rights reserved.

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Chip-and-trace mark for the NullGate controller. */
public final class NullGateBrandView extends View {
    private static final int GREEN = Color.rgb(58, 175, 105);
    private static final int SAND = Color.rgb(231, 207, 173);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public NullGateBrandView(Context context) { super(context); setMinimumHeight(dp(166)); }

    @Override protected void onDraw(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float size = dp(68), top = dp(18), left = centerX - size / 2f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.35f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(GREEN);

        // The reference mark uses individually routed board traces, not a fan.
        drawPair(canvas, left, size, top + dp(12), dp(26), top - dp(2), dp(82));
        drawPair(canvas, left, size, top + dp(24), dp(38), top + dp(8), dp(104));
        drawPair(canvas, left, size, top + dp(34), dp(48), top + dp(34), dp(12));
        drawPair(canvas, left, size, top + dp(46), dp(35), top + dp(61), dp(73));
        drawPair(canvas, left, size, top + dp(57), dp(23), top + dp(73), dp(91));

        canvas.drawLine(centerX, top, centerX, dp(3), paint);
        canvas.drawCircle(centerX, dp(3), dp(3), paint);
        for (int i = 0; i < 7; i++) {
            float pinX = left + dp(7 + i * 9);
            canvas.drawLine(pinX, top - dp(6), pinX, top, paint);
            canvas.drawLine(pinX, top + size, pinX, top + size + dp(7), paint);
            float pinY = top + dp(7 + i * 9);
            canvas.drawLine(left - dp(6), pinY, left, pinY, paint);
            canvas.drawLine(left + size, pinY, left + size + dp(6), pinY, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(10, 112, 54));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size), dp(9), dp(9), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(29, 139, 72));
        canvas.drawRoundRect(new RectF(left + dp(3), top + dp(3), left + size - dp(3), top + size - dp(3)), dp(7), dp(7), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SAND);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(42));
        paint.setFakeBoldText(true);
        canvas.drawText("∅", centerX, top + dp(48), paint);

        paint.setColor(GREEN);
        paint.setTextSize(dp(35));
        paint.setFakeBoldText(true);
        float titleY = top + size + dp(47);
        canvas.drawText("NULLGATE", centerX, titleY, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        float titleTrace = titleY - dp(9);
        canvas.drawLine(0, titleTrace, dp(34), titleTrace, paint);
        canvas.drawLine(dp(34), titleTrace, dp(52), titleTrace - dp(18), paint);
        canvas.drawLine(dp(52), titleTrace - dp(18), dp(68), titleTrace - dp(18), paint);
        canvas.drawCircle(dp(8), titleTrace, dp(3), paint);
        canvas.drawLine(getWidth(), titleTrace, getWidth() - dp(34), titleTrace, paint);
        canvas.drawLine(getWidth() - dp(34), titleTrace, getWidth() - dp(52), titleTrace - dp(18), paint);
        canvas.drawLine(getWidth() - dp(52), titleTrace - dp(18), getWidth() - dp(68), titleTrace - dp(18), paint);
        canvas.drawCircle(getWidth() - dp(8), titleTrace, dp(3), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setFakeBoldText(false);
        paint.setColor(Color.rgb(116, 78, 55));
        paint.setTextSize(dp(14));
        canvas.drawText("PiXi temporary privilege control", centerX, titleY + dp(24), paint);
    }

    private void drawPair(Canvas canvas, float chipLeft, float chipSize, float startY,
            float run, float endY, float terminalInset) {
        float leftStart = chipLeft - dp(6);
        float leftElbow = chipLeft - run;
        float leftTerminal = terminalInset;
        canvas.drawLine(leftStart, startY, leftElbow, startY, paint);
        canvas.drawLine(leftElbow, startY, leftElbow - dp(18), endY, paint);
        canvas.drawLine(leftElbow - dp(18), endY, leftTerminal + dp(4), endY, paint);
        canvas.drawCircle(leftTerminal, endY, dp(3), paint);

        float rightStart = chipLeft + chipSize + dp(6);
        float rightElbow = chipLeft + chipSize + run;
        float rightTerminal = getWidth() - terminalInset;
        canvas.drawLine(rightStart, startY, rightElbow, startY, paint);
        canvas.drawLine(rightElbow, startY, rightElbow + dp(18), endY, paint);
        canvas.drawLine(rightElbow + dp(18), endY, rightTerminal - dp(4), endY, paint);
        canvas.drawCircle(rightTerminal, endY, dp(3), paint);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
