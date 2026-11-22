package com.sclean.cachecleaner;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class STheme {
    static final int MINT = Color.rgb(59, 229, 191);
    static final int DARK = Color.rgb(7, 24, 39);
    static final int INK = Color.rgb(10, 35, 54);
    static final int DARK_CARD = Color.rgb(16, 43, 63);
    static final int DARK_MUTED = Color.rgb(156, 182, 196);
    static final int LIGHT_BG = Color.rgb(242, 248, 249);
    static final int LIGHT_TEXT = Color.rgb(18, 44, 60);
    static final int LIGHT_MUTED = Color.rgb(89, 111, 126);

    private STheme() { }

    static boolean dark(Context ctx) {
        return ctx.getSharedPreferences("s_cleaner", Context.MODE_PRIVATE).getBoolean("dark", true);
    }

    static void setDark(Context ctx, boolean enabled) {
        ctx.getSharedPreferences("s_cleaner", Context.MODE_PRIVATE).edit().putBoolean("dark", enabled).apply();
    }

    static int bg(Context c) { return dark(c) ? DARK : LIGHT_BG; }
    static int text(Context c) { return dark(c) ? Color.WHITE : LIGHT_TEXT; }
    static int secondary(Context c) { return dark(c) ? DARK_MUTED : LIGHT_MUTED; }
    static int cardColor(Context c) { return dark(c) ? DARK_CARD : Color.WHITE; }
    static int iconBg(Context c) { return dark(c) ? 0xFF1C4F58 : 0xFFDDF8F2; }

    static int dp(Context c, float value) {
        return Math.round(c.getResources().getDisplayMetrics().density * value);
    }

    static GradientDrawable rounded(int color, float r, Context c) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(c, r));
        return drawable;
    }

    static GradientDrawable gradient(Context c, int start, int end, float radius) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        bg.setCornerRadius(dp(c, radius));
        return bg;
    }

    static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView label = new TextView(c);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setIncludeFontPadding(true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return label;
    }

    static void space(Context c, LinearLayout l, int height) {
        l.addView(new View(c), new LinearLayout.LayoutParams(1, dp(c, height)));
    }

    static LinearLayout column(Context c) {
        LinearLayout c1 = new LinearLayout(c);
        c1.setOrientation(LinearLayout.VERTICAL);
        return c1;
    }

    static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    static LinearLayout panel(Context c) {
        LinearLayout panel = column(c);
        panel.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        panel.setBackground(rounded(cardColor(c), 21, c));
        if (!dark(c)) panel.setElevation(dp(c, 2));
        return panel;
    }

    static TextView pill(Context c, String caption, boolean accent) {
        TextView b = text(c, caption, 14, accent ? INK : text(c), true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setMinHeight(dp(c, 50));
        b.setBackground(rounded(accent ? MINT : cardColor(c), 16, c));
        return b;
    }

    static void insets(Activity activity, View root, int left, int top, int right, int bottom) {
        root.setPadding(dp(activity, left), dp(activity, top), dp(activity, right), dp(activity, bottom));
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets cutout = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(dp(activity, left) + cutout.left, dp(activity, top) + cutout.top,
                        dp(activity, right) + cutout.right, dp(activity, bottom) + cutout.bottom);
                return insets;
            });
        }
    }

    static final class Icon extends View {
        private final String kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context context, String kind) { super(context); this.kind = kind; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            float scale = Math.min(getWidth(), getHeight()) / 48f;
            canvas.translate((getWidth() - 48f * scale) * .5f, (getHeight() - 48f * scale) * .5f);
            canvas.scale(scale, scale);
            p.reset();
            p.setAntiAlias(true);
            p.setColor(MINT);
            p.setStrokeWidth(2.8f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            switch (kind) {
                case "clean":
                    p.setStyle(Paint.Style.FILL);
                    star(canvas, 23, 23, 14, 5);
                    star(canvas, 36, 10, 5, 2);
                    star(canvas, 9, 36, 5, 2);
                    break;
                case "junk":
                    canvas.drawRoundRect(new RectF(14, 14, 34, 39), 3, 3, p);
                    canvas.drawLine(11, 12, 37, 12, p);
                    canvas.drawLine(20, 8, 28, 8, p);
                    canvas.drawLine(21, 22, 21, 32, p);
                    canvas.drawLine(27, 22, 27, 32, p);
                    break;
                case "dupes":
                    canvas.drawRoundRect(new RectF(9, 16, 32, 39), 4, 4, p);
                    canvas.drawRoundRect(new RectF(17, 9, 40, 32), 4, 4, p);
                    canvas.drawLine(24, 21, 34, 21, p);
                    break;
                case "large":
                    canvas.drawLine(10, 36, 38, 36, p);
                    canvas.drawRoundRect(new RectF(11, 23, 17, 34), 1, 1, p);
                    canvas.drawRoundRect(new RectF(21, 17, 27, 34), 1, 1, p);
                    canvas.drawRoundRect(new RectF(31, 10, 37, 34), 1, 1, p);
                    break;
                case "download":
                    canvas.drawLine(24, 9, 24, 30, p);
                    canvas.drawLine(15, 22, 24, 31, p);
                    canvas.drawLine(33, 22, 24, 31, p);
                    canvas.drawRoundRect(new RectF(10, 34, 38, 39), 2, 2, p);
                    break;
                case "apps":
                    for (int x=0; x<2; x++) for (int y=0; y<2; y++)
                        canvas.drawRoundRect(new RectF(9+x*17, 9+y*17, 22+x*17, 22+y*17), 3,3,p);
                    break;
                case "analyze":
                    canvas.drawArc(new RectF(9,9,39,39),-90,275,false,p);
                    canvas.drawLine(24,24,24,12,p);
                    canvas.drawLine(24,24,35,24,p);
                    break;
                case "monitor":
                    Path line = new Path();
                    line.moveTo(5,27); line.lineTo(14,27); line.lineTo(20,14);
                    line.lineTo(27,36); line.lineTo(33,21); line.lineTo(38,27);
                    line.lineTo(44,27);
                    canvas.drawPath(line,p);
                    break;
                default:
                    canvas.drawRoundRect(new RectF(10,12,38,36),5,5,p);
                    canvas.drawLine(15,23,33,23,p);
            }
            canvas.restoreToCount(save);
        }
        private void star(Canvas c, float x, float y, float outer, float inner) {
            Path path = new Path();
            path.moveTo(x, y-outer);
            path.lineTo(x+inner, y-inner);
            path.lineTo(x+outer, y);
            path.lineTo(x+inner, y+inner);
            path.lineTo(x, y+outer);
            path.lineTo(x-inner, y+inner);
            path.lineTo(x-outer, y);
            path.lineTo(x-inner, y-inner);
            path.close();
            c.drawPath(path,p);
        }
    }
}
