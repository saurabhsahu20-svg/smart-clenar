package com.sclean.cachecleaner;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

final class StorageRingView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double percent = 0d;
    private double animated = 0d;
    private ValueAnimator animator;
    StorageRingView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Storage usage visualization");
    }
    void setPercent(double value) {
        double next=Math.min(100d, Math.max(0d, value));
        if(Math.abs(percent-next)<.1d)return;
        percent=next;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat((float)animated, (float)percent);
        animator.setDuration(820L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(v -> {
            animated = ((Float)v.getAnimatedValue()).doubleValue();
            invalidate();
        });
        animator.start();
    }
    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        int desired = STheme.dp(getContext(), 214);
        int h = MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(hs) : desired;
        setMeasuredDimension(w, h);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f, cy = getHeight() / 2f + 6*density;
        float radius = Math.min(getHeight()*.36f, getWidth()*.27f);
        float stroke = 19*density;
        RectF arc = new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
        p.reset(); p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx,cy,radius*1.9f,
                new int[]{0xFF1C5265,0xFF0B2B44,0xFF0A2740},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,radius+stroke*.60f,p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(stroke);
        p.setColor(0xFF0B2638);
        RectF bottom = new RectF(arc);
        bottom.offset(0,7*density);
        canvas.drawArc(bottom, -90, 360, false,p);
        p.setColor(0xFF23465B);
        canvas.drawArc(arc,-90,360,false,p);
        p.setColor(0xFF08635E);
        canvas.drawArc(bottom,-90,(float)(3.6*animated),false,p);
        p.setShader(new SweepGradient(cx,cy,
                new int[]{0xFF39E5BF,0xFF9CF8D5,0xFF1FC9CB,0xFF39E5BF},
                new float[]{0f,.30f,.75f,1f}));
        canvas.drawArc(arc,-90,(float)(3.6*animated),false,p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 1));
        p.setTextSize(34*density);
        canvas.drawText(String.format(java.util.Locale.US,"%.0f%%",animated),
                cx,cy+4*density,p);
        p.setTextSize(11*density);
        p.setTypeface(android.graphics.Typeface.DEFAULT);
        p.setColor(0xFFB3D6D9);
        canvas.drawText("STORAGE USED",cx,cy+25*density,p);
    }
}
