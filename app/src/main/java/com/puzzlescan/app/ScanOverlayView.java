package com.puzzlescan.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ScanOverlayView extends View {
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int stateColor = Color.WHITE;
    private String hint = "Поместите одну деталь в рамку";

    public ScanOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(3));
        shade.setColor(0x33000000);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(dp(14));
        text.setFakeBoldText(true);
        setWillNotDraw(false);
    }

    public void setState(int color, String hint) {
        this.stateColor = color;
        this.hint = hint;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        RectF box = new RectF(w * 0.16f, h * 0.18f, w * 0.84f, h * 0.78f);

        canvas.drawRect(0, 0, w, box.top, shade);
        canvas.drawRect(0, box.bottom, w, h, shade);
        canvas.drawRect(0, box.top, box.left, box.bottom, shade);
        canvas.drawRect(box.right, box.top, w, box.bottom, shade);

        border.setColor(stateColor);
        canvas.drawRoundRect(box, dp(16), dp(16), border);
        canvas.drawLine(w / 2f - dp(18), h * 0.48f, w / 2f + dp(18), h * 0.48f, border);
        canvas.drawLine(w / 2f, h * 0.48f - dp(18), w / 2f, h * 0.48f + dp(18), border);
        canvas.drawText(hint, w / 2f, box.bottom - dp(14), text);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
