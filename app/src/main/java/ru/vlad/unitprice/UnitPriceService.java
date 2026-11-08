package ru.vlad.unitprice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public final class UnitPriceService extends AccessibilityService {

    private static final long DEBOUNCE_MS = 250;
    private static final long SCROLL_END_MS = 180;
    private static final long HOLD_MS = 350;
    private static final int MAX_NODES = 1500;
    private static final int MAX_DEPTH = 60;

    private OverlayManager overlay;
    private String currentPackage = "";
    private boolean scrolling = false;
    private boolean holding = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scanTask = this::scan;
    private final Runnable endScrollTask = () -> {
        scrolling = false;
        scan();
    };
    private final Runnable holdTask = () -> {
        holding = true;
        if (overlay != null) {
            overlay.hide();
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sp, key) -> {
        if (Prefs.KEY_OVERLAY.equals(key)) {
            if (!sp.getBoolean(key, true)) {
                if (overlay != null) {
                    overlay.clear();
                }
            } else {
                scan();
            }
        }
    };

    @Override
    public void onServiceConnected() {
        overlay = new OverlayManager(this);
        scrolling = false;
        holding = false;
        getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefListener);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_SCROLLED
                    | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
                    | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            info.notificationTimeout = 50;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int type = event.getEventType();

        // Касания приходят с packageName = null, поэтому обрабатываем их до всех проверок.
        // Прячем не по любому тапу, а только если палец задержался на экране.
        if (type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            handler.removeCallbacks(holdTask);
            handler.postDelayed(holdTask, HOLD_MS);
            return;
        }
        if (type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
            handler.removeCallbacks(holdTask);
            if (holding) {
                holding = false;
                handler.removeCallbacks(scanTask);
                handler.postDelayed(scanTask, 100);
            }
            return;
        }

        if (event.getPackageName() == null) {
            return;
        }
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) {
            return;
        }
        if (!Prefs.allApps(this) && !Prefs.SUPPORTED.contains(pkg)) {
            return;
        }
        currentPackage = pkg;

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Смена экрана: старые чипы гасим сразу, новые считаем чуть позже.
            scrolling = false;
            holding = false;
            handler.removeCallbacks(holdTask);
            if (overlay != null) {
                overlay.hide();
            }
            handler.removeCallbacks(endScrollTask);
            handler.removeCallbacks(scanTask);
            handler.postDelayed(scanTask, 120);
            return;
        }

        if (holding) {
            return;
        }

        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            handler.removeCallbacks(scanTask);
            handler.removeCallbacks(holdTask);
            holding = false;
            // На время скролла чипы прячем: догонять контент они всё равно не успевают.
            if (!scrolling) {
                scrolling = true;
                if (overlay != null) {
                    overlay.hide();
                }
            }
            handler.removeCallbacks(endScrollTask);
            handler.postDelayed(endScrollTask, SCROLL_END_MS);
            return;
        }

        if (scrolling) {
            return;
        }

        handler.removeCallbacks(scanTask);
        handler.postDelayed(scanTask, DEBOUNCE_MS);
    }

    @Override
    public void onInterrupt() {
        scrolling = false;
        holding = false;
        handler.removeCallbacks(holdTask);
        if (overlay != null) {
            overlay.clear();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(scanTask);
        handler.removeCallbacks(endScrollTask);
        handler.removeCallbacks(holdTask);
        try {
            getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(prefListener);
        } catch (Exception ignored) {
        }
        if (overlay != null) {
            overlay.clear();
        }
        super.onDestroy();
    }

    private void scan() {
        if (overlay == null) {
            return;
        }
        if (!Prefs.overlayEnabled(this)) {
            overlay.clear();
            return;
        }
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Exception e) {
            return;
        }
        if (root == null) {
            overlay.clear();
            return;
        }

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;

        List<CardBuilder.TextNode> nodes = new ArrayList<>();
        List<Rect> images = new ArrayList<>();
        collect(root, nodes, images, 0, width, height);

        boolean kopeckMode = "ru.pyaterochka.app.browser".equals(currentPackage);
        overlay.render(CardBuilder.build(nodes, images, width, kopeckMode));
    }

    private void collect(AccessibilityNodeInfo node, List<CardBuilder.TextNode> out,
                         List<Rect> images, int depth, int screenW, int screenH) {
        if (node == null || depth > MAX_DEPTH || out.size() >= MAX_NODES) {
            return;
        }

        Rect r = new Rect();
        node.getBoundsInScreen(r);
        boolean validBounds = r.width() > 0 && r.height() > 0;

        CharSequence text = node.getText();
        String value = (text != null && text.length() > 0) ? text.toString() : null;

        // Ozon (и некоторые другие) кладут цену в content-desc: "price_205₽".
        if (value == null) {
            CharSequence desc = node.getContentDescription();
            if (desc != null && desc.length() > 0) {
                value = desc.toString();
                if (value.startsWith("price_")) {
                    value = value.substring("price_".length());
                }
            }
        }

        if (value != null && value.length() > 0 && validBounds
                && r.bottom > 0 && r.top < screenH) {
            out.add(new CardBuilder.TextNode(value.trim(), new Rect(r)));
        }

        String cls = node.getClassName() == null ? "" : node.getClassName().toString();
        boolean looksLikeImage = cls.contains("ImageView") || cls.equals("android.view.View");
        if (looksLikeImage && validBounds
                && r.width() >= 150 && r.height() >= 200
                && r.width() <= screenW
                && r.height() <= screenH * 0.7) {
            images.add(new Rect(r));
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collect(child, out, images, depth + 1, screenW, screenH);
            }
        }
    }
}
