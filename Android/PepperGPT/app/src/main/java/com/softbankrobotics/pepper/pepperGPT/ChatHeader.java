package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;

public final class ChatHeader {
    public static void apply(Activity activity, String theme) {
        int id = activity.getResources().getIdentifier("toolbar", "id", activity.getPackageName());
        View toolbar = activity.findViewById(id);
        if (toolbar == null) return;
        ThemePalette palette = ThemePalette.named(theme);
        int statusId = activity.getResources().getIdentifier("statusRow", "id", activity.getPackageName());
        View status = activity.findViewById(statusId);
        if (status instanceof LinearLayout) {
            LinearLayout row = (LinearLayout)status;
            float density = row.getResources().getDisplayMetrics().density;
            row.setTag("pepper-status-row");
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(0, 0, 0, 0);
            tintStatus(row, palette.muted);
            moveStatusInline(toolbar, row, density);
        }
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        tint(toolbar, palette);
        // The toolbar populates its overflow and custom controls after the first theme pass.
        toolbar.post(() -> tint(toolbar, palette));
    }

    public static void bindMenu(Activity activity, Menu menu) {
        int id = activity.getResources().getIdentifier("toolbar", "id", activity.getPackageName());
        View view = activity.findViewById(id);
        if (!(view instanceof Toolbar)) return;
        Toolbar toolbar = (Toolbar)view;
        if (bindOverflow(activity, toolbar, menu)) return;
        toolbar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (bindOverflow(activity, toolbar, menu)) toolbar.removeOnLayoutChangeListener(this);
            }
        });
        toolbar.requestLayout();
    }

    private static boolean bindOverflow(Activity activity, Toolbar toolbar, Menu menu) {
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            if (!(toolbar.getChildAt(i) instanceof ActionMenuView)) continue;
            ActionMenuView actions = (ActionMenuView)toolbar.getChildAt(i);
            for (int j = 0; j < actions.getChildCount(); j++) {
                View button = actions.getChildAt(j);
                if (!(button.getLayoutParams() instanceof ActionMenuView.LayoutParams)
                        || !((ActionMenuView.LayoutParams)button.getLayoutParams()).isOverflowButton) continue;
                button.setOnTouchListener(null);
                button.setOnClickListener(anchor -> showMenu(activity, anchor, menu));
                return true;
            }
        }
        return false;
    }

    private static void showMenu(Activity activity, View anchor, Menu menu) {
        ThemePalette theme = ThemePalette.named(activity.getSharedPreferences("PepperGPT_Prefs", 0)
                .getString("ui_theme", "glass"));
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, Math.round(5 * density), 0, Math.round(5 * density));
        PopupWindow popup = new PopupWindow(rows, Math.round(252 * density), -2, true);
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(theme.surface);
        panel.setStroke(Math.round(density), theme.line);
        panel.setCornerRadius(Math.round(12 * density));
        popup.setBackgroundDrawable(panel);
        popup.setOutsideTouchable(true);
        popup.setElevation(Math.round(8 * density));
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;
            TextView row = new TextView(activity);
            row.setText(item.getTitle());
            row.setTextSize(16);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(Math.round(16 * density), 0, Math.round(16 * density), 0);
            row.setTextColor(item.isEnabled() ? theme.ink : theme.muted);
            row.setEnabled(item.isEnabled());
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x447f9db1), null, mask));
            row.setOnClickListener(clicked -> {
                popup.dismiss();
                activity.onOptionsItemSelected(item);
            });
            rows.addView(row, new LinearLayout.LayoutParams(-1, Math.round(44 * density)));
        }
        popup.showAsDropDown(anchor, 0, Math.round(4 * density), Gravity.END);
    }

    private static void moveStatusInline(View toolbar, LinearLayout status, float density) {
        if (!(toolbar instanceof Toolbar) || !(status.getParent() instanceof ViewGroup)
                || status.getParent() == toolbar) return;
        ((ViewGroup)status.getParent()).removeView(status);
        Toolbar.LayoutParams inline = new Toolbar.LayoutParams(-2, -1, Gravity.END | Gravity.CENTER_VERTICAL);
        inline.setMarginEnd(Math.round(18 * density));
        ((Toolbar)toolbar).addView(status, inline);
    }

    private static void tintStatus(View view, int ink) {
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            label.setTextColor(ink);
            if (label.getParent() instanceof LinearLayout) {
                LinearLayout group = (LinearLayout)label.getParent();
                int index = group.indexOfChild(label);
                if (index > 0 && group.getChildAt(index - 1) instanceof ImageView) {
                    String clean = label.getText().toString().trim();
                    if (!clean.contentEquals(label.getText())) label.setText(clean);
                    int gap = Math.round(6 * label.getResources().getDisplayMetrics().density);
                    label.setPaddingRelative(gap, label.getPaddingTop(), label.getPaddingEnd(), label.getPaddingBottom());
                }
            }
            for (Drawable icon : label.getCompoundDrawables()) {
                if (icon != null) icon.setColorFilter(ink, PorterDuff.Mode.SRC_IN);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintStatus(group.getChildAt(i), ink);
        }
    }

    private static void tint(View view, ThemePalette palette) {
        if ("pepper-status-row".equals(view.getTag())) return;
        if (view instanceof TextView) ((TextView)view).setTextColor(palette.ink);
        if (view instanceof ImageView) ((ImageView)view).setColorFilter(palette.ink);
        if ("pepper-voice-volume".equals(view.getTag())) {
            float density = view.getResources().getDisplayMetrics().density;
            GradientDrawable group = new GradientDrawable();
            group.setColor(palette.surface);
            group.setStroke(Math.round(density), palette.line);
            group.setCornerRadius(18 * density);
            view.setBackground(group);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tint(group.getChildAt(i), palette);
        }
    }
}
