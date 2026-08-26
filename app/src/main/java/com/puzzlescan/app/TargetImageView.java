package com.puzzlescan.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.ImageView;

public class TargetImageView extends ImageView {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] polygon;
    private int refWidth = 1;
    private int refHeight = 1;
    private boolean ambiguous = false;

    public TargetImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(4));
    }

    public void setReferenceBitmap(Bitmap bitmap) {
        refWidth = bitmap.getWidth();
        refHeight = bitmap.getHeight();
        setImageBitmap(bitmap);
        clearTarget();
    }

    public void showTarget(float[] polygon, boolean ambiguous) {
        this.polygon = polygon;
        this.ambiguous = ambiguous;
        invalidate();
    }

    public void clearTarget() {
        this.polygon = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (polygon == null || polygon.length < 6 || getDrawable() == null) return;

        float[] mapped = polygon.clone();
        Matrix m = getImageMatrix();
        m.mapPoints(mapped);

        int c = ambiguous ? Color.rgb(242, 169, 0) : Color.rgb(54, 182, 107);
        fill.setColor(ambiguous ? 0x55F2A900 : 0x5536B66B);
        stroke.setColor(c);

        Path p = new Path();
        p.moveTo(mapped[0], mapped[1]);
        for (int i = 2; i < mapped.length; i += 2) p.lineTo(mapped[i], mapped[i + 1]);
        p.close();
        canvas.drawPath(p, fill);
        canvas.drawPath(p, stroke);

        float cx = 0, cy = 0;
        int n = mapped.length / 2;
        for (int i = 0; i < mapped.length; i += 2) {
            cx += mapped[i];
            cy += mapped[i + 1];
        }
        cx /= n;
        cy /= n;
        canvas.drawCircle(cx, cy, dp(9), stroke);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
