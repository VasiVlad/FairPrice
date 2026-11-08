package ru.vlad.unitprice;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Рисует чипы «₽/кг» поверх чужих приложений.
 * Предпочтительно — в правом верхнем углу фотографии товара.
 */
public final class OverlayManager {

    private final Context context;
    private final WindowManager wm;
    private final float density;
    private final int screenWidth;
    private final int screenHeight;
    private final List<TextView> chips = new ArrayList<>();

    public OverlayManager(Context context) {
        this.context = context;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.density = context.getResources().getDisplayMetrics().density;
        this.screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        this.screenHeight = context.getResources().getDisplayMetrics().heightPixels;
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private TextView create() {
        TextView tv = new TextView(context);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11f);
        tv.setPadding(dp(6), dp(2), dp(6), dp(2));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC207A3A);
        bg.setCornerRadius(dp(6));
        tv.setBackground(bg);
        return tv;
    }

    public void hide() {
        for (TextView tv : chips) {
            tv.setVisibility(View.GONE);
        }
    }

    public void render(List<CardBuilder.Card> cards) {
        List<CardBuilder.Card> visible = new ArrayList<>();
        for (CardBuilder.Card c : cards) {
            if (c.anchor == null) {
                continue;
            }
            if (c.anchor.bottom < 0 || c.anchor.top > screenHeight) {
                continue;
            }
            double per = c.perUnit();
            if (per < 10 || per > 50000) {
                continue;
            }
            visible.add(c);
        }

        for (int i = 0; i < visible.size(); i++) {
            CardBuilder.Card c = visible.get(i);

            TextView tv;
            if (i < chips.size()) {
                tv = chips.get(i);
            } else {
                tv = create();
                wm.addView(tv, params());
                chips.add(tv);
            }

            String unit = "л".equals(c.unit) ? "л" : "кг";
            tv.setText(Math.round(c.perUnit()) + " ₽/" + unit);
            tv.setVisibility(View.VISIBLE);
            tv.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int chipW = tv.getMeasuredWidth();
            int chipH = tv.getMeasuredHeight();

            int x;
            int y;
            if (c.photo != null) {
                // Правый верхний угол фотографии, отступ минимальный.
                x = c.photo.right - chipW - dp(2);
                y = c.photo.top + dp(2);
            } else {
                // Фолбэк: над ценником.
                x = c.anchor.left;
                y = c.anchor.top - chipH - dp(6);
                if (y < dp(30)) {
                    y = c.anchor.bottom + dp(4);
                }
            }

            x = Math.max(0, Math.min(x, screenWidth - chipW - dp(2)));
            y = Math.max(0, Math.min(y, screenHeight - chipH - dp(2)));

            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) tv.getLayoutParams();
            if (lp == null) {
                lp = params();
            }
            lp.x = x;
            lp.y = y;

            try {
                wm.updateViewLayout(tv, lp);
            } catch (Exception ignored) {
            }
        }

        for (int i = visible.size(); i < chips.size(); i++) {
            chips.get(i).setVisibility(View.GONE);
        }
    }

    public void clear() {
        for (TextView tv : chips) {
            try {
                wm.removeView(tv);
            } catch (Exception ignored) {
            }
        }
        chips.clear();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
