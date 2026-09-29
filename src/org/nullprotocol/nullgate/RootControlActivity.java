package org.nullprotocol.nullgate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.nullprotocol.nullgate.protocol.RootSessionProtocol;
import java.util.ArrayList;
import java.util.List;

/** Choose app, root ON, open app, make changes, close app, root OFF. */
public final class RootControlActivity extends Activity {
    private static final java.util.concurrent.ExecutorService TRANSPORT =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final RootBrokerClient broker = new RootBrokerClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView brokerStatus, sessionStatus, retainedStatus, instructions, log;
    private EditText selected;
    private Switch rootSwitch;
    private Button launch, stop;
    private String target;
    private RootSessionProtocol.Response confirmed;
    private boolean busy, refreshing, visible;
    private final Runnable poll = new Runnable() {
        public void run() { if (visible) { checkStatus(); handler.postDelayed(this, 3000); } }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        target = getPreferences(MODE_PRIVATE).getString("target", "com.drdisagree.colorblendr");
        if (android.os.Build.VERSION.SDK_INT >= 31) getWindow().setHideOverlayWindows(true);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildView()); render();
    }
    @Override public void onResume() { super.onResume(); visible = true; handler.post(poll); }
    @Override public void onPause() { visible = false; handler.removeCallbacks(poll); super.onPause(); }

    private View buildView() {
        ScrollView screen = new ScrollView(this); screen.setFillViewport(true);
        screen.setBackground(gradient(Color.rgb(230,214,193), Color.rgb(224,200,168), 0, 0));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14),dp(30),dp(14),dp(104));
        screen.addView(page, new ScrollView.LayoutParams(-1,-2));
        ImageView header = image("nullgate_header_reference", "NullGate chip and circuit mark");
        header.setScaleType(ImageView.ScaleType.FIT_XY);
        page.addView(header, new LinearLayout.LayoutParams(-1,dp(174)));

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(4),dp(16),dp(4));
        card.setBackground(gradient(Color.argb(76,255,255,255),Color.argb(50,255,249,238),dp(12),dp(1)));
        brokerStatus = statusRow(card,"nullgate_icon_gear","Broker: checking",true);
        sessionStatus = statusRow(card,"nullgate_icon_layers","Root access: checking",true);
        retainedStatus = statusRow(card,"nullgate_icon_shield","Applied changes stay",false);
        page.addView(card, params(-1,-2,dp(8)));

        instructions = text("",12,Color.rgb(89,53,36));
        instructions.setPadding(dp(8),dp(2),dp(8),dp(4)); page.addView(instructions);
        TextView label = text("Choose app",13,Color.rgb(116,78,55));
        label.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        label.setPadding(dp(7),dp(3),0,dp(4)); page.addView(label);
        selected = new EditText(this); selected.setSingleLine(true); selected.setText(target);
        selected.setFocusable(false); selected.setClickable(true); selected.setTextSize(16);
        selected.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        selected.setPadding(dp(18),0,dp(18),0); selected.setTextColor(Color.rgb(75,39,27));
        selected.setHintTextColor(Color.rgb(148,111,83));
        selected.setBackground(gradient(Color.argb(80,255,255,255),Color.argb(44,255,249,238),dp(10),dp(1)));
        selected.setOnClickListener(v -> chooseApp()); page.addView(selected,params(-1,dp(50),dp(10)));

        TextView note = text("Root access lasts until you switch it OFF.\nThe selected app needs a NullGate root connection.",13,Color.rgb(116,78,55));
        note.setPadding(dp(7),dp(3),0,dp(10)); page.addView(note);
        rootSwitch = new Switch(this); rootSwitch.setText("Root broker"); rootSwitch.setTextSize(16);
        rootSwitch.setFilterTouchesWhenObscured(true);
        rootSwitch.setTextColor(Color.rgb(75,39,27)); rootSwitch.setPadding(dp(18),0,dp(18),0);
        rootSwitch.setBackground(gradient(Color.argb(80,255,255,255),Color.argb(44,255,249,238),dp(10),dp(1)));
        rootSwitch.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
                new int[]{Color.rgb(121,88,69),Color.rgb(224,200,168)}));
        rootSwitch.setTrackTintList(ColorStateList.valueOf(Color.rgb(148,111,83)));
        // Click sends intent; only the broker's response changes the displayed switch.
        rootSwitch.setOnClickListener(v -> {
            if (confirmed == null || busy) { render(); return; }
            RootSessionProtocol.Operation op = confirmed.state == RootSessionProtocol.State.OFF
                    ? RootSessionProtocol.Operation.ON : RootSessionProtocol.Operation.OFF;
            render(); transact(op, op == RootSessionProtocol.Operation.ON ? target : "");
        });
        page.addView(rootSwitch,params(-1,dp(52),dp(8)));
        launch = button("Open selected app",true); launch.setOnClickListener(v -> openSelected());
        page.addView(launch,params(-1,dp(52),dp(8)));
        stop = button("Switch root OFF",false);
        stop.setFilterTouchesWhenObscured(true);
        stop.setBackground(gradient(Color.rgb(121,88,69),Color.rgb(141,106,86),dp(9),0));
        stop.setOnClickListener(v -> transact(RootSessionProtocol.Operation.OFF,""));
        page.addView(stop,params(-1,dp(48),dp(15)));
        ImageView audit = image("nullgate_audit_header_reference","Local audit log circuit heading");
        audit.setScaleType(ImageView.ScaleType.FIT_XY); page.addView(audit,params(-1,dp(39),dp(5)));
        log = text("",9,Color.rgb(103,70,50)); log.setTypeface(Typeface.MONOSPACE);
        log.setPadding(dp(16),dp(13),dp(16),dp(13)); log.setMinHeight(dp(116));
        log.setBackground(gradient(Color.argb(72,255,255,255),Color.argb(42,255,249,238),dp(9),dp(1)));
        page.addView(log,params(-1,-2,dp(6)));
        Button check = button("Refresh broker status",true); check.setOnClickListener(v -> checkStatus());
        page.addView(check,params(-1,dp(38),dp(24))); return screen;
    }

    private void chooseApp() {
        if (busy || confirmed == null || confirmed.state != RootSessionProtocol.State.OFF) return;
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        apps.sort((a,b) -> a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        List<String> packages = new ArrayList<>(), labels = new ArrayList<>();
        for (ResolveInfo app : apps) {
            String name = app.activityInfo.packageName;
            if (getPackageName().equals(name) || packages.contains(name)) continue;
            packages.add(name); labels.add(app.loadLabel(getPackageManager()) + "\n" + name);
        }
        new AlertDialog.Builder(this).setTitle("Choose app").setItems(labels.toArray(new String[0]), (dialog,index) -> {
            target = packages.get(index); getPreferences(MODE_PRIVATE).edit().putString("target",target).apply();
            selected.setText(target); render();
        }).show();
    }
    private void openSelected() {
        if (confirmed == null || confirmed.state != RootSessionProtocol.State.ON) return;
        Intent app = getPackageManager().getLaunchIntentForPackage(confirmed.target);
        if (app == null) { append("The selected app has no launch screen."); return; }
        try { startActivity(app); }
        catch (Exception failure) { append("Unable to open the selected app."); }
    }
    private void checkStatus() {
        if (busy || refreshing || isDestroyed()) return;
        refreshing = true;
        TRANSPORT.execute(() -> {
            RootSessionProtocol.Response result = null;
            try { result = broker.exchange(RootSessionProtocol.Operation.STATUS,""); } catch (Exception unavailable) { }
            final RootSessionProtocol.Response reply = result;
            runOnUiThread(() -> { refreshing = false; if (!isDestroyed()) { confirmed = reply; render(); } });
        });
    }
    private void transact(RootSessionProtocol.Operation operation, String requested) {
        if (busy) return;
        busy = true; render();
        TRANSPORT.execute(() -> {
            RootSessionProtocol.Response result = null;
            try { result = broker.exchange(operation,requested); } catch (Exception unknown) { }
            final RootSessionProtocol.Response reply = result;
            runOnUiThread(() -> {
                busy = false; if (isDestroyed()) return; confirmed = reply;
                if (reply == null) append("No confirmed reply. Refresh status to check the switch.");
                else append("Root access: " + reply.state + (reply.target.isEmpty() ? "" : " — " + reply.target)
                        + (reply.code == RootSessionProtocol.Code.OK ? "" : " (" + reply.code + ")"));
                render();
            });
        });
    }
    private void render() {
        boolean known = confirmed != null;
        boolean active = known && confirmed.state == RootSessionProtocol.State.ON;
        boolean off = known && confirmed.state == RootSessionProtocol.State.OFF;
        if (active || (known && confirmed.state == RootSessionProtocol.State.FAULT)) {
            target = confirmed.target; selected.setText(target);
            getPreferences(MODE_PRIVATE).edit().putString("target",target).apply();
        }
        brokerStatus.setText(known ? "Broker: connected" : "Broker: unavailable");
        sessionStatus.setText("Root access: " + (known ? confirmed.state.toString() : "unknown"));
        retainedStatus.setText("Applied changes stay");
        rootSwitch.setChecked(active); rootSwitch.setText("Root broker: " + (known ? confirmed.state : "unknown"));
        rootSwitch.setEnabled(known && !busy); selected.setEnabled(off && !busy);
        launch.setEnabled(active && !busy); stop.setEnabled(!off && !busy);
        instructions.setText(busy ? "Updating root access…" : !known ? "Start the root broker from DoloWOLF, then refresh status."
                : active ? "Open the selected app, make your changes, close it, then switch root OFF."
                : off ? "Choose an app and switch root ON. Applied changes remain after OFF."
                : "Cleanup is unresolved. Use Switch root OFF to retry.");
        log.setText(getPreferences(MODE_PRIVATE).getString("root_log",""));
    }
    private void append(String line) {
        String prior = getPreferences(MODE_PRIVATE).getString("root_log","");
        String content = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss",System.currentTimeMillis()) + "  " + line + "\n" + prior;
        getPreferences(MODE_PRIVATE).edit().putString("root_log",content.substring(0,Math.min(content.length(),16384))).apply(); render();
    }
    private ImageView image(String name,String description) {
        ImageView image = new ImageView(this); image.setImageResource(getResources().getIdentifier(name,"drawable",getPackageName()));
        image.setContentDescription(description); return image;
    }
    private TextView text(String value,int size,int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private TextView statusRow(LinearLayout card,String name,String label,boolean divider) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = image(name,""); badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(badge,new LinearLayout.LayoutParams(dp(39),dp(39)));
        TextView value = text(label,15,Color.rgb(74,37,25));
        value.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); value.setPadding(dp(16),0,0,0);
        row.addView(value,new LinearLayout.LayoutParams(0,dp(49),1)); card.addView(row);
        if (divider) { View line = new View(this); line.setBackgroundColor(Color.argb(45,116,78,55)); card.addView(line,new LinearLayout.LayoutParams(-1,dp(1))); }
        return value;
    }
    private Button button(String label,boolean quiet) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT,Typeface.BOLD); button.setTextColor(Color.rgb(251,241,224));
        button.setBackgroundTintList(null); button.setGravity(Gravity.CENTER);
        button.setBackground(gradient(Color.rgb(92,68,51),Color.rgb(110,82,63),dp(9),0));
        if (quiet) {
            button.setTextSize(11); button.setTextColor(Color.rgb(98,64,44));
            button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(220,194,160)));
        }
        return button;
    }
    private GradientDrawable gradient(int start,int end,int radius,int stroke) {
        GradientDrawable value = new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});
        value.setCornerRadius(radius); if (stroke > 0) value.setStroke(stroke,Color.argb(95,139,98,64)); return value;
    }
    private LinearLayout.LayoutParams params(int width,int height,int bottom) {
        LinearLayout.LayoutParams value = new LinearLayout.LayoutParams(width,height); value.setMargins(0,0,0,bottom); return value;
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
