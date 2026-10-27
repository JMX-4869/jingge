package com.xiaojing.jingge;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 从接口拉模型列表，点一个就用；拉不到可以手动填。 */
final class ModelPicker {
    private ModelPicker() {
    }

    static String label(Activity a) {
        return "模型：" + Ai.model(a) + "  ▾";
    }

    /** 设置页里的那个「模型」行：点开拉列表，也能手动填。 */
    static TextView button(final Activity act) {
        final TextView t = new TextView(act);
        t.setText(label(act));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(Ui.TEXT);
        t.setPadding(Ui.dp(act, 10), Ui.dp(act, 11), Ui.dp(act, 10), Ui.dp(act, 11));
        t.setBackground(Ui.round(Ui.PANEL_UP, Ui.dp(act, 8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(act, 8);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(act, new Runnable() {
                    @Override
                    public void run() {
                        t.setText(label(act));
                    }
                });
            }
        });
        return t;
    }

    static void show(final Activity act, final Runnable after) {
        final Dialog d = new Dialog(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.PANEL);
        box.setPadding(Ui.dp(act, 18), Ui.dp(act, 16), Ui.dp(act, 18), Ui.dp(act, 12));

        TextView title = new TextView(act);
        title.setText("选择模型");
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTextColor(Ui.TEXT);
        box.addView(title);

        final LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 260));
        slp.topMargin = Ui.dp(act, 10);
        box.addView(sv, slp);

        TextView loading = new TextView(act);
        loading.setText("拉取中…");
        loading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        loading.setTextColor(Ui.MUTED);
        loading.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
        list.addView(loading);

        TextView manual = new TextView(act);
        manual.setText("手动填写");
        manual.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        manual.setTextColor(Ui.MUTED);
        manual.setGravity(Gravity.END);
        manual.setPadding(0, Ui.dp(act, 10), 0, 0);
        manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                typeModel(act, after);
            }
        });
        box.addView(manual);

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (act.getResources().getDisplayMetrics().widthPixels * 0.88f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();

        Ai.listModels(act, new Ai.ModelsCb() {
            @Override
            public void onModels(final List<String> models, String error) {
                list.removeAllViews();
                if (error != null || models == null || models.isEmpty()) {
                    TextView t = new TextView(act);
                    t.setText(error != null ? "拉取失败：" + error : "接口没返回模型");
                    t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                    t.setTextColor(Ui.MUTED);
                    t.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8));
                    list.addView(t);
                    return;
                }
                String cur = Ai.model(act);
                for (final String m : models) {
                    TextView t = new TextView(act);
                    t.setText(m.equals(cur) ? m + "  ·当前" : m);
                    t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                    t.setTextColor(m.equals(cur) ? Ui.ACCENT : Ui.TEXT);
                    t.setPadding(0, Ui.dp(act, 11), 0, Ui.dp(act, 11));
                    t.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Ai.prefs(act).edit().putString("ai_model", m).apply();
                            d.dismiss();
                            after.run();
                        }
                    });
                    list.addView(t);
                }
            }
        });
    }

    private static void typeModel(final Activity act, final Runnable after) {
        final Dialog d = new Dialog(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.PANEL);
        box.setPadding(Ui.dp(act, 18), Ui.dp(act, 16), Ui.dp(act, 18), Ui.dp(act, 12));

        TextView title = new TextView(act);
        title.setText("模型名");
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTextColor(Ui.TEXT);
        box.addView(title);

        final EditText input = new EditText(act);
        input.setText(Ai.model(act));
        input.setTextColor(Ui.TEXT);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        input.setSingleLine(true);
        input.setBackground(Ui.round(Ui.PANEL_UP, Ui.dp(act, 8)));
        input.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = Ui.dp(act, 10);
        box.addView(input, ilp);

        TextView ok = new TextView(act);
        ok.setText("用这个");
        ok.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        ok.setTextColor(Ui.ACCENT);
        ok.setGravity(Gravity.END);
        ok.setPadding(0, Ui.dp(act, 12), 0, 0);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String m = input.getText().toString().trim();
                if (m.length() == 0) {
                    Toast.makeText(act, "写个名字", Toast.LENGTH_SHORT).show();
                    return;
                }
                Ai.prefs(act).edit().putString("ai_model", m).apply();
                d.dismiss();
                after.run();
            }
        });
        box.addView(ok);

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (act.getResources().getDisplayMetrics().widthPixels * 0.88f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }
}
