package org.nullprotocol.nullgate;

// Copyright © 2026 Null Protocol. All rights reserved.

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * NullGate's controller never opens a root shell. A separate rooted-ADB broker
 * will eventually claim and verify its one-time lease requests.
 */
public final class MainActivity extends Activity {
    private static final String LOG_KEY = "activity_log";
    private static final String CONTROLLER_PACKAGE = "org.nullprotocol.nullgate";
    private static final String SELF_TEST_CAPABILITY = "NULLGATE_EPHEMERAL_MARKER";
    private static final java.util.concurrent.ExecutorService TRANSPORT =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private LeaseRequest pending;
    private String pendingState = "NONE";
    private TextView status;
    private TextView brokerStatus;
    private TextView leaseCount;
    private TextView exposureCount;
    private TextView logView;
    private EditText targetInput;
    private EditText seedInput;
    private final BrokerClient broker = new BrokerClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildView());
        restorePending();
        append("Controller opened. Device privilege state is not inferred from this screen.");
        if (pending != null) recoverRestoredPending();
        refresh();
    }

    private View buildView() {
        int pad = dp(14);
        ScrollView screen = new ScrollView(this);
        screen.setFillViewport(true);
        screen.setBackground(makeGradient(
                Color.rgb(230, 214, 193), Color.rgb(224, 200, 168), 0, 0));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        // Keep the final self-test fully above persistent three-button navigation bars.
        page.setPadding(pad, dp(30), pad, dp(104));
        screen.addView(page, new ScrollView.LayoutParams(-1, -2));

        ImageView brand = new ImageView(this);
        brand.setImageResource(getResources().getIdentifier(
                "nullgate_header_reference", "drawable", getPackageName()));
        brand.setScaleType(ImageView.ScaleType.FIT_XY);
        brand.setContentDescription("NullGate chip and circuit mark");
        page.addView(brand, new LinearLayout.LayoutParams(-1, dp(174)));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(4), dp(16), dp(4));
        statusCard.setBackground(makeGradient(
                Color.argb(76, 255, 255, 255), Color.argb(50, 255, 249, 238), dp(12), dp(1)));
        brokerStatus = statusRow(statusCard, "nullgate_icon_gear", "Broker: status unverified", true);
        leaseCount = statusRow(statusCard, "nullgate_icon_layers", "Active leases: 0", true);
        exposureCount = statusRow(statusCard, "nullgate_icon_shield", "Persistent root exposure: 0", false);
        page.addView(statusCard, marginParams(-1, -2, 0, dp(8)));

        status = text("", 12, Color.rgb(89, 53, 36));
        status.setPadding(dp(8), dp(2), dp(8), dp(4));
        page.addView(status);

        TextView targetLabel = text("Target package name", 13, Color.rgb(116, 78, 55));
        targetLabel.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        targetLabel.setPadding(dp(7), dp(3), 0, dp(4));
        page.addView(targetLabel);

        targetInput = new EditText(this);
        targetInput.setHint("Target package name");
        targetInput.setText("com.drdisagree.colorblendr");
        targetInput.setSingleLine(true);
        targetInput.setEnabled(false);
        targetInput.setTextSize(16);
        targetInput.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        targetInput.setPadding(dp(18), 0, dp(18), 0);
        targetInput.setTextColor(Color.rgb(75, 39, 27));
        targetInput.setHintTextColor(Color.rgb(148, 111, 83));
        targetInput.setBackground(makeGradient(
                Color.argb(80, 255, 255, 255), Color.argb(44, 255, 249, 238), dp(10), dp(1)));
        page.addView(targetInput, marginParams(-1, dp(50), 0, dp(10)));

        TextView seedLabel = text("Temporary system-theme seed", 13, Color.rgb(116, 78, 55));
        seedLabel.setPadding(dp(7), dp(3), 0, dp(4));
        page.addView(seedLabel);
        seedInput = new EditText(this);
        seedInput.setText("#76543A");
        seedInput.setSingleLine(true);
        seedInput.setTextSize(16);
        seedInput.setPadding(dp(18), 0, dp(18), 0);
        seedInput.setTextColor(Color.rgb(75, 39, 27));
        seedInput.setBackground(makeGradient(Color.argb(80,255,255,255),
                Color.argb(44,255,249,238),dp(10),dp(1)));
        page.addView(seedInput, marginParams(-1, dp(50), 0, dp(10)));

        Button nativeTheme = button("Apply 60-second native theme lease");
        nativeTheme.setOnClickListener(v -> {
            try {
                String hex = seedInput.getText().toString().trim();
                if (!hex.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
                int seed = (int)(0xff000000L | Long.parseLong(hex.substring(1),16));
                submitSystemTheme(seed, 60 * 1000L);
            } catch (IllegalArgumentException invalid) {
                append("Denied invalid theme seed; use #RRGGBB."); refresh();
            }
        });
        page.addView(nativeTheme, marginParams(-1, dp(52), 0, dp(8)));

        Button request = quietButton("ColorBlendr client integration — awaiting client patch");
        request.setEnabled(false);
        page.addView(request, marginParams(-1, dp(52), 0, dp(8)));

        Button selfTest = quietButton("Run 60-second safe broker self-test");
        selfTest.setOnClickListener(v -> submitLease(
                CONTROLLER_PACKAGE, SELF_TEST_CAPABILITY, 60 * 1000L));

        Button revoke = button("Revoke pending request now");
        revoke.setBackground(makeGradient(
                Color.rgb(121, 88, 69), Color.rgb(141, 106, 86), dp(9), 0));
        revoke.setOnClickListener(v -> {
            if (pending == null) append("Revoke requested: nothing was pending.");
            else revoke(pending);
            refresh();
        });
        page.addView(revoke, marginParams(-1, dp(48), 0, dp(15)));

        ImageView auditHeader = new ImageView(this);
        auditHeader.setImageResource(getResources().getIdentifier(
                "nullgate_audit_header_reference", "drawable", getPackageName()));
        auditHeader.setScaleType(ImageView.ScaleType.FIT_XY);
        auditHeader.setContentDescription("Local audit log circuit heading");
        page.addView(auditHeader, marginParams(-1, dp(39), 0, dp(5)));
        logView = text("", 9, Color.rgb(103, 70, 50));
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(16), dp(13), dp(16), dp(13));
        logView.setMinHeight(dp(116));
        logView.setBackground(makeGradient(
                Color.argb(72, 255, 255, 255), Color.argb(42, 255, 249, 238), dp(9), dp(1)));
        page.addView(logView, marginParams(-1, -2, 0, dp(6)));
        page.addView(selfTest, marginParams(-1, dp(38), 0, dp(24)));
        return screen;
    }

    private void submitLease(String target, String capability, long durationMillis) {
        if (pending != null) {
            append("Denied new request while another lease is unresolved.");
            refresh();
            return;
        }
        pending = LeaseRequest.forTarget(
                target, capability, SystemClock.elapsedRealtime(), durationMillis);
        pendingState = "SUBMITTING — outcome not yet confirmed";
        if (!savePending()) {
            pending = null;
            append("Request not sent: unable to save recovery record.");
            refresh();
            return;
        }
        LeaseRequest requestLease = pending;
        append("Submitting " + capability + " lease for " + target + ": " + pending.id);
        refresh();
        TRANSPORT.execute(() -> {
            try {
                String code = broker.issue(requestLease).code;
                persistBrokerPhase(code);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    append("Broker decision for " + requestLease.id + ": " + code);
                    if (pending == requestLease) {
                        if ("GRANTED".equals(code)) {
                            pendingState = "ACTIVE — broker accepted; cleanup must be confirmed";
                            brokerStatus.setText("Broker: connected");
                            scheduleVisibleExpiry(requestLease);
                        }
                        else if ("CLEANUP_FAILED".equals(code)) {
                            pendingState = "CLEANUP FAILED — stop testing and reconcile on host";
                        } else { clearPending(); }
                    }
                    refresh();
                });
            } catch (java.io.IOException error) {
                persistPhase("UNKNOWN");
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    append("No trustworthy reply. The request may have executed; use revoke and verify cleanup.");
                    if (pending == requestLease) pendingState = "UNKNOWN — revoke confirmation required";
                    brokerStatus.setText("Broker: unavailable or unverified");
                    refresh();
                });
            }
        });
    }

    private void submitSystemTheme(int seedArgb, long durationMillis) {
        if (pending != null) { append("Denied new request while another lease is unresolved."); refresh(); return; }
        pending = LeaseRequest.forSystemTheme(seedArgb, "TONAL_SPOT",
                SystemClock.elapsedRealtime(), durationMillis);
        submitPreparedLease();
    }

    private void submitPreparedLease() {
        pendingState = "SUBMITTING — outcome not yet confirmed";
        if (!savePending()) { pending=null; append("Request not sent: unable to save recovery record."); refresh(); return; }
        LeaseRequest requestLease=pending;
        append("Submitting " + requestLease.capability + " lease: " + requestLease.id); refresh();
        transmit(requestLease);
    }

    private void transmit(LeaseRequest requestLease) {
        TRANSPORT.execute(() -> {
            try {
                String code=broker.issue(requestLease).code;
                persistBrokerPhase(code);
                runOnUiThread(() -> { if(isDestroyed())return; append("Broker decision for "+requestLease.id+": "+code);
                    if(pending==requestLease){ if("GRANTED".equals(code)){pendingState="ACTIVE — broker accepted; cleanup must be confirmed";brokerStatus.setText("Broker: connected");scheduleVisibleExpiry(requestLease);} else if("CLEANUP_FAILED".equals(code)){pendingState="CLEANUP FAILED — stop testing and reconcile on host";} else clearPending(); } refresh(); });
            } catch(java.io.IOException error){ persistPhase("UNKNOWN"); runOnUiThread(() -> {if(isDestroyed())return;append("No trustworthy reply. The request may have executed; use revoke and verify cleanup.");if(pending==requestLease)pendingState="UNKNOWN — revoke confirmation required";brokerStatus.setText("Broker: unavailable or unverified");refresh();}); }
        });
    }

    private void persistBrokerPhase(String code) {
        if ("GRANTED".equals(code)) persistPhase("ACTIVE");
        else if ("CLEANUP_FAILED".equals(code)) persistPhase("CLEANUP_FAILED");
        else persistPhase("FINISHED");
    }

    private void persistPhase(String phase) {
        getPreferences(MODE_PRIVATE).edit().putString("lease_phase", phase).commit();
    }

    private void scheduleVisibleExpiry(LeaseRequest lease) {
        long delay = Math.max(0L, lease.expiresAtElapsedMillis - SystemClock.elapsedRealtime());
        mainHandler.postDelayed(() -> {
            if (pending == lease) {
                append("Lease deadline elapsed locally: " + lease.id);
                revoke(lease);
            }
        }, delay);
    }

    private void revoke(LeaseRequest lease) {
        pendingState = "REVOKE REQUESTED — awaiting broker confirmation";
        persistPhase("REVOKING");
        append("Submitting revoke request: " + lease.id);
        refresh();
        TRANSPORT.execute(() -> {
            try {
                String code = broker.revoke(lease.id).code;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    append("Broker revoke decision: " + code);
                    if (pending == lease && "REVOKED".equals(code)) {
                        clearPending();
                    } else if (pending == lease) {
                        pendingState = "CLEANUP UNCONFIRMED — " + code + "; host verification required";
                    }
                    refresh();
                });
            } catch (java.io.IOException error) {
                persistPhase("UNKNOWN");
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    append("Revoke unconfirmed. Do not assume the effect has been removed.");
                    if (pending == lease) pendingState = "REVOKE UNCONFIRMED — host verification required";
                    refresh();
                });
            }
        });
    }

    private boolean savePending() {
        return getPreferences(MODE_PRIVATE).edit()
                .putString("lease_id", pending.id).putString("lease_nonce", pending.nonce)
                .putString("lease_target", pending.targetPackage).putString("lease_cap", pending.capability)
                .putLong("lease_issued", pending.issuedAtElapsedMillis)
                .putLong("lease_expiry", pending.expiresAtElapsedMillis)
                .putInt("lease_seed", pending.seedArgb).putString("lease_style", pending.themeStyle)
                .putString("lease_phase", "SUBMITTING").commit();
    }

    private void restorePending() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String id = prefs.getString("lease_id", null);
        if (id == null) return;
        pending = new LeaseRequest(id, prefs.getString("lease_nonce", ""),
                prefs.getString("lease_target", ""), prefs.getString("lease_cap", ""),
                prefs.getLong("lease_issued", 0), prefs.getLong("lease_expiry", 0));
        if ("SYSTEM_THEME_SEED_APPLY".equals(pending.capability))
            pending = new LeaseRequest(id, prefs.getString("lease_nonce", ""), "android",
                    pending.capability, prefs.getLong("lease_issued",0), prefs.getLong("lease_expiry",0),
                    prefs.getInt("lease_seed",0), prefs.getString("lease_style","TONAL_SPOT"));
        String phase = prefs.getString("lease_phase", "UNKNOWN");
        pendingState = "ACTIVE".equals(phase)
                ? "ACTIVE — recovered after Android theme refresh"
                : "RECOVERED — verifying cleanup, not resubmitting";
    }

    private void recoverRestoredPending() {
        LeaseRequest recovered = pending;
        String phase = getPreferences(MODE_PRIVATE).getString("lease_phase", "UNKNOWN");
        if ("ACTIVE".equals(phase)) {
            scheduleVisibleExpiry(recovered);
            return;
        }
        // A theme overlay can recreate this Activity before the broker reply reaches the old UI.
        // Give the background exchange one bounded window to persist its outcome, then fail closed.
        mainHandler.postDelayed(() -> {
            if (pending != recovered) return;
            String settled = getPreferences(MODE_PRIVATE).getString("lease_phase", "UNKNOWN");
            if ("ACTIVE".equals(settled)) {
                pendingState = "ACTIVE — recovered after Android theme refresh";
                brokerStatus.setText("Broker: connected");
                scheduleVisibleExpiry(recovered);
                refresh();
            } else {
                revoke(recovered);
            }
        }, 2500L);
    }

    private void clearPending() {
        if (!getPreferences(MODE_PRIVATE).edit().remove("lease_id").remove("lease_phase").commit()) {
            pendingState = "Unable to clear recovery record — retry revoke";
            return;
        }
        pending = null;
        pendingState = "NONE";
    }

    @Override protected void onResume() {
        super.onResume();
        if (pending != null && SystemClock.elapsedRealtime() >= pending.expiresAtElapsedMillis
                && pendingState.startsWith("ACTIVE")) revoke(pending);
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.rgb(251, 241, 224));
        button.setBackgroundTintList(null);
        button.setBackground(makeGradient(
                Color.rgb(92, 68, 51), Color.rgb(110, 82, 63), dp(9), 0));
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private Button quietButton(String label) {
        Button button = button(label);
        button.setTextSize(11);
        button.setTextColor(Color.rgb(98, 64, 44));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(220, 194, 160)));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private void refresh() {
        boolean active = pending != null && pendingState.startsWith("ACTIVE");
        leaseCount.setText("Active leases: " + (active ? "1" : "0"));
        exposureCount.setText("Persistent root exposure: 0");
        status.setText(pending == null ? "" : pending.display(pendingState));
        logView.setText(getPreferences(MODE_PRIVATE).getString(LOG_KEY, ""));
    }

    private TextView statusRow(LinearLayout card, String iconResource, String label, boolean divider) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = new ImageView(this);
        badge.setImageResource(getResources().getIdentifier(
                iconResource, "drawable", getPackageName()));
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(badge, new LinearLayout.LayoutParams(dp(39), dp(39)));
        TextView value = text(label, 15, Color.rgb(74, 37, 25));
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setPadding(dp(16), 0, 0, 0);
        row.addView(value, new LinearLayout.LayoutParams(0, dp(49), 1));
        card.addView(row);
        if (divider) {
            View line = new View(this);
            line.setBackgroundColor(Color.argb(45, 116, 78, 55));
            card.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        return value;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(0, top, 0, bottom);
        return params;
    }

    private GradientDrawable makeGradient(int start, int end, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        if (stroke > 0) drawable.setStroke(stroke, Color.argb(95, 139, 98, 64));
        return drawable;
    }

    private void append(String event) {
        String prior = getPreferences(MODE_PRIVATE).getString(LOG_KEY, "");
        String line = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", System.currentTimeMillis())
                + "  " + event + "\n";
        getPreferences(MODE_PRIVATE).edit().putString(LOG_KEY, (line + prior).substring(0, Math.min(16_384, line.length() + prior.length()))).apply();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
