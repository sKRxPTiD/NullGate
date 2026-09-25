package org.nullprotocol.nullgate.testclient;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.security.MessageDigest;

/** First-party external-app harness. It receives a receipt, never root access. */
public final class MainActivity extends Activity {
    private static final String CONTROLLER_PACKAGE = "org.nullprotocol.nullgate";
    private static final String CONTROLLER_ACTIVITY =
            "org.nullprotocol.nullgate.ClientRequestActivity";
    private static final String ACTION_REQUEST =
            "org.nullprotocol.nullgate.action.REQUEST_THEME_LEASE";
    private static final String ACTION_REVOKE =
            "org.nullprotocol.nullgate.action.REVOKE_LEASE";
    private static final String EXTRA_PROTOCOL_VERSION = "protocolVersion";
    private static final String EXTRA_SEED_ARGB = "seedArgb";
    private static final String EXTRA_THEME_STYLE = "themeStyle";
    private static final String EXTRA_DURATION_MILLIS = "durationMillis";
    private static final String EXTRA_DECISION = "decision";
    private static final String EXTRA_LEASE_ID = "leaseId";
    private static final String EXTRA_EXPIRES_ELAPSED = "expiresElapsed";
    private static final int REQUEST_LEASE = 1001;
    private static final int REQUEST_REVOKE = 1002;
    private static final int TEST_SEED_ARGB = 0xff876a4b;
    private static final long TEST_DURATION_MILLIS = 60_000L;
    private static final String STORE = "test_client_state";

    private TextView status;
    private Button requestButton;
    private Button revokeButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (android.os.Build.VERSION.SDK_INT >= 31) getWindow().setHideOverlayWindows(true);
        setContentView(buildPage());
        refreshState();
    }

    private View buildPage() {
        int pad = dp(24);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(pad, dp(48), pad, dp(48));
        page.setBackgroundColor(Color.rgb(230, 214, 193));

        TextView mark = label("∅ NULLGATE", 27, Color.rgb(0, 104, 61));
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(mark);
        TextView title = label("External-app test client", 21, Color.rgb(74, 37, 25));
        title.setPadding(0, dp(20), 0, dp(12));
        page.addView(title);
        TextView explanation = label(
                "This first-party app tests NullGate exactly as an external client. "
                + "It can request one typed 60-second theme lease and receive only a "
                + "receipt. It never receives root, a shell, or broker access.",
                16, Color.rgb(89, 53, 36));
        page.addView(explanation, new LinearLayout.LayoutParams(-1, 0, 1));

        status = label("Checking the paired NullGate controller…", 14,
                Color.rgb(116, 78, 55));
        status.setPadding(0, dp(18), 0, dp(18));
        page.addView(status);

        requestButton = actionButton("Request 60-second test lease", Color.rgb(0, 104, 61));
        requestButton.setFilterTouchesWhenObscured(true);
        requestButton.setOnClickListener(v -> requestLease());
        page.addView(requestButton, new LinearLayout.LayoutParams(-1, dp(56)));

        revokeButton = actionButton("Revoke active test lease", Color.rgb(103, 70, 50));
        revokeButton.setFilterTouchesWhenObscured(true);
        revokeButton.setOnClickListener(v -> revokeLease());
        LinearLayout.LayoutParams revokeParams = new LinearLayout.LayoutParams(-1, dp(52));
        revokeParams.setMargins(0, dp(12), 0, 0);
        page.addView(revokeButton, revokeParams);
        return page;
    }

    private void requestLease() {
        if (!pairedControllerInstalled()) {
            status.setText("Denied: the installed controller is absent or not signed by the paired key.");
            refreshButtons(false);
            return;
        }
        requestButton.setEnabled(false);
        status.setText("Opening NullGate's protected approval screen…");
        Intent intent = explicit(ACTION_REQUEST)
                .putExtra(EXTRA_PROTOCOL_VERSION, 1)
                .putExtra(EXTRA_SEED_ARGB, TEST_SEED_ARGB)
                .putExtra(EXTRA_THEME_STYLE, "TONAL_SPOT")
                .putExtra(EXTRA_DURATION_MILLIS, TEST_DURATION_MILLIS);
        startActivityForResult(intent, REQUEST_LEASE);
    }

    private void revokeLease() {
        String leaseId = store().getString(EXTRA_LEASE_ID, null);
        if (leaseId == null || leaseId.isEmpty()) {
            status.setText("No recorded test lease to revoke.");
            refreshState();
            return;
        }
        if (!pairedControllerInstalled()) {
            status.setText("Cannot verify the paired controller; revocation was not attempted.");
            refreshButtons(false);
            return;
        }
        requestButton.setEnabled(false);
        revokeButton.setEnabled(false);
        status.setText("Opening NullGate to revoke the recorded lease…");
        startActivityForResult(explicit(ACTION_REVOKE)
                .putExtra(EXTRA_PROTOCOL_VERSION, 1)
                .putExtra(EXTRA_LEASE_ID, leaseId), REQUEST_REVOKE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LEASE) handleLeaseResult(resultCode, data);
        else if (requestCode == REQUEST_REVOKE) handleRevokeResult(resultCode, data);
    }

    private void handleLeaseResult(int resultCode, Intent data) {
        try {
            String decision = validatedDecision(data, true);
            if (resultCode == RESULT_OK && "GRANTED".equals(decision)) {
                TestClientResponsePolicy.Receipt receipt =
                        TestClientResponsePolicy.requireFreshGrant(
                                true, data.getExtras().keySet(), decision,
                                data.getStringExtra(EXTRA_LEASE_ID),
                                data.getLongExtra(EXTRA_EXPIRES_ELAPSED, -1L),
                                SystemClock.elapsedRealtime());
                if (store().edit().putString(EXTRA_LEASE_ID, receipt.leaseId)
                        .putLong(EXTRA_EXPIRES_ELAPSED, receipt.expiresElapsed).commit()) {
                    status.setText("GRANTED: receipt stored; hard expiry is active.");
                    refreshButtons(true);
                    return;
                }
                throw new SecurityException("receipt persistence failed");
            }
            status.setText("Not granted: " + decision);
        } catch (SecurityException invalid) {
            status.setText("Not granted: DENIED_INVALID_RESULT");
        }
        refreshState();
    }

    private void handleRevokeResult(int resultCode, Intent data) {
        try {
            String decision = validatedDecision(data, false);
            if (TestClientResponsePolicy.isConfirmedRevoke(
                    resultCode == RESULT_OK, data.getExtras().keySet(), decision)) {
                store().edit().clear().commit();
                status.setText("REVOKED: NullGate confirmed cleanup.");
            } else {
                status.setText("Revocation not confirmed: " + decision);
            }
        } catch (SecurityException invalid) {
            status.setText("Revocation not confirmed: DENIED_INVALID_RESULT");
        }
        refreshButtons(store().getString(EXTRA_LEASE_ID, null) != null);
    }

    private String validatedDecision(Intent data, boolean leaseResponse) {
        if (data == null || data.getExtras() == null)
            throw new SecurityException("missing result");
        return TestClientResponsePolicy.validateDecision(
                data.getExtras().keySet(), data.getStringExtra(EXTRA_DECISION), leaseResponse);
    }

    private void refreshState() {
        if (!pairedControllerInstalled()) {
            status.setText("Paired NullGate controller not installed or signer mismatch.");
            refreshButtons(false);
            return;
        }
        String leaseId = store().getString(EXTRA_LEASE_ID, null);
        if (leaseId == null) {
            status.setText("Ready. Controller and test client share the expected first-party signer.");
            refreshButtons(false);
            return;
        }
        long remaining = store().getLong(EXTRA_EXPIRES_ELAPSED, 0L)
                - SystemClock.elapsedRealtime();
        status.setText(remaining > 0
                ? "Recorded lease active for at most " + ((remaining + 999L) / 1000L) + " seconds."
                : "Recorded lease has expired; revoke to reconcile and confirm cleanup.");
        refreshButtons(true);
    }

    private void refreshButtons(boolean hasLease) {
        requestButton.setEnabled(!hasLease && pairedControllerInstalled());
        revokeButton.setEnabled(hasLease && pairedControllerInstalled());
    }

    private boolean pairedControllerInstalled() {
        try {
            return soleSignerDigest(getPackageName())
                    .equals(soleSignerDigest(CONTROLLER_PACKAGE));
        } catch (Exception absentOrUntrusted) {
            return false;
        }
    }

    private String soleSignerDigest(String packageName) throws Exception {
        PackageInfo info = getPackageManager().getPackageInfo(
                packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        if (info.signingInfo == null) throw new SecurityException("signer unavailable");
        Signature[] signatures = info.signingInfo.getApkContentsSigners();
        if (signatures == null || signatures.length != 1)
            throw new SecurityException("sole current signer required");
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(signatures[0].toByteArray());
        StringBuilder value = new StringBuilder(64);
        for (byte part : digest) value.append(String.format("%02x", part & 0xff));
        return value.toString();
    }

    private static Intent explicit(String action) {
        return new Intent(action).setComponent(
                new ComponentName(CONTROLLER_PACKAGE, CONTROLLER_ACTIVITY));
    }

    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value); text.setTextSize(size); text.setTextColor(color);
        return text;
    }

    private Button actionButton(String value, int color) {
        Button button = new Button(this);
        button.setText(value); button.setAllCaps(false);
        button.setTextColor(Color.WHITE); button.setTextSize(16);
        button.setBackgroundColor(color);
        return button;
    }

    private SharedPreferences store() { return getSharedPreferences(STORE, MODE_PRIVATE); }
    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
