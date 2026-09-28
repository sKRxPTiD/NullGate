package org.nullprotocol.nullgate.themeclient;

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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.security.MessageDigest;
import org.nullprotocol.nullgate.protocol.ExternalClientContract;
import org.nullprotocol.nullgate.protocol.ExternalResultPolicy;

/** First-party typed theme client. It receives a lease receipt, never privilege. */
public final class MainActivity extends Activity {
    private static final String CONTROLLER_PACKAGE = "org.nullprotocol.nullgate";
    private static final String CONTROLLER_ACTIVITY =
            "org.nullprotocol.nullgate.ClientRequestActivity";
    private static final int REQUEST_LEASE = 2001;
    private static final int REQUEST_REVOKE = 2002;
    private static final int REQUEST_RECONCILE = 2003;
    private static final long LEASE_DURATION_MILLIS = 120_000L;
    private static final String STORE = "theme_client_state";
    private static final String[] PALETTE_NAMES = {
            "Null green", "Signal violet", "Ember"
    };
    private static final int[] PALETTE_SEEDS = {
            0xff204f46, 0xff6750a4, 0xff9b3d20
    };
    private static final String[] STYLE_NAMES = {
            "TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "MONOCHROMATIC"
    };

    private TextView status;
    private Spinner palette;
    private Spinner style;
    private Button requestButton;
    private Button revokeButton;
    private Button reconcileButton;

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
        page.setPadding(pad, dp(40), pad, dp(72));
        page.setBackgroundColor(Color.rgb(19, 24, 23));

        TextView mark = label("∅ NULLGATE", 26, Color.rgb(84, 214, 152));
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(mark);
        TextView title = label("Theme Client", 22, Color.WHITE);
        title.setPadding(0, dp(14), 0, dp(8));
        page.addView(title);
        TextView explanation = label(
                "Choose a bounded palette and style. NullGate will show the exact "
                + "request before granting a two-minute lease and will restore the "
                + "previous theme on revoke or expiry.",
                15, Color.rgb(190, 204, 198));
        explanation.setPadding(0, 0, 0, dp(14));
        page.addView(explanation);

        page.addView(label("Palette", 13, Color.rgb(128, 178, 154)));
        palette = spinner(PALETTE_NAMES);
        page.addView(palette, new LinearLayout.LayoutParams(-1, dp(52)));
        page.addView(label("Style", 13, Color.rgb(128, 178, 154)));
        style = spinner(STYLE_NAMES);
        page.addView(style, new LinearLayout.LayoutParams(-1, dp(52)));

        status = label("Checking the paired NullGate controller…", 14,
                Color.rgb(174, 195, 185));
        status.setPadding(0, dp(16), 0, dp(16));
        page.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));

        requestButton = actionButton("Request two-minute theme lease",
                Color.rgb(0, 112, 72));
        requestButton.setOnClickListener(v -> requestLease());
        page.addView(requestButton, new LinearLayout.LayoutParams(-1, dp(56)));

        revokeButton = actionButton("Restore previous theme now", Color.rgb(105, 64, 48));
        revokeButton.setOnClickListener(v -> revokeLease());
        LinearLayout.LayoutParams revokeParams = new LinearLayout.LayoutParams(-1, dp(52));
        revokeParams.setMargins(0, dp(10), 0, 0);
        page.addView(revokeButton, revokeParams);

        reconcileButton = actionButton("Reconcile uncertain result", Color.rgb(72, 82, 78));
        reconcileButton.setOnClickListener(v -> reconcileLease());
        LinearLayout.LayoutParams reconcileParams = new LinearLayout.LayoutParams(-1, dp(52));
        reconcileParams.setMargins(0, dp(10), 0, 0);
        page.addView(reconcileButton, reconcileParams);
        return page;
    }

    private void requestLease() {
        if (!pairedControllerInstalled()) {
            status.setText("Denied: the paired NullGate controller is unavailable.");
            refreshButtons(ThemeClientStatePolicy.UNKNOWN);
            return;
        }
        int paletteIndex = palette.getSelectedItemPosition();
        int styleIndex = style.getSelectedItemPosition();
        if (paletteIndex < 0 || paletteIndex >= PALETTE_SEEDS.length
                || styleIndex < 0 || styleIndex >= STYLE_NAMES.length) {
            status.setText("Selection is invalid; no request was sent.");
            return;
        }
        requestButton.setEnabled(false);
        status.setText("Opening NullGate's protected approval screen…");
        if (!store().edit().putString("phase", ThemeClientStatePolicy.PENDING).commit()) {
            status.setText("Request not sent: durable pending state could not be recorded.");
            refreshButtons(ThemeClientStatePolicy.UNKNOWN);
            return;
        }
        startActivityForResult(explicit(ExternalClientContract.ACTION_REQUEST_THEME)
                .putExtra(ExternalClientContract.EXTRA_PROTOCOL_VERSION, 1)
                .putExtra(ExternalClientContract.EXTRA_SEED_ARGB,
                        PALETTE_SEEDS[paletteIndex])
                .putExtra(ExternalClientContract.EXTRA_THEME_STYLE,
                        STYLE_NAMES[styleIndex])
                .putExtra(ExternalClientContract.EXTRA_DURATION_MILLIS,
                        LEASE_DURATION_MILLIS), REQUEST_LEASE);
    }

    private void revokeLease() {
        String leaseId = store().getString(ExternalClientContract.EXTRA_LEASE_ID, null);
        if (leaseId == null || leaseId.isEmpty()) {
            status.setText("No recorded theme lease to revoke.");
            refreshState();
            return;
        }
        if (!pairedControllerInstalled()) {
            status.setText("Controller trust is uncertain; revocation was not attempted.");
            refreshButtons(ThemeClientStatePolicy.UNKNOWN);
            return;
        }
        disableActions();
        status.setText("Opening NullGate to restore the previous theme…");
        startActivityForResult(explicit(ExternalClientContract.ACTION_REVOKE)
                .putExtra(ExternalClientContract.EXTRA_PROTOCOL_VERSION, 1)
                .putExtra(ExternalClientContract.EXTRA_LEASE_ID, leaseId), REQUEST_REVOKE);
    }

    private void reconcileLease() {
        if (!pairedControllerInstalled()) {
            status.setText("Controller trust is uncertain; reconciliation was not attempted.");
            refreshButtons(ThemeClientStatePolicy.UNKNOWN);
            return;
        }
        disableActions();
        status.setText("Opening NullGate to reconcile the uncertain result…");
        startActivityForResult(explicit(ExternalClientContract.ACTION_RECONCILE)
                .putExtra(ExternalClientContract.EXTRA_PROTOCOL_VERSION, 1),
                REQUEST_RECONCILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LEASE) handleLeaseResult(resultCode, data);
        else if (requestCode == REQUEST_REVOKE) handleRevokeResult(resultCode, data);
        else if (requestCode == REQUEST_RECONCILE) handleReconcileResult(resultCode, data);
    }

    private void handleLeaseResult(int resultCode, Intent data) {
        if (data != null && data.getExtras() != null) {
            String decision = data.getStringExtra(ExternalClientContract.EXTRA_DECISION);
            ExternalResultPolicy.Evaluation evaluated =
                    ExternalResultPolicy.evaluateLeaseResult(
                            resultCode == RESULT_OK, resultCode == RESULT_CANCELED,
                            data.getExtras().keySet(), decision,
                            data.getStringExtra(ExternalClientContract.EXTRA_LEASE_ID),
                            data.getLongExtra(
                                    ExternalClientContract.EXTRA_EXPIRES_ELAPSED, -1L),
                            SystemClock.elapsedRealtime(), LEASE_DURATION_MILLIS);
            if (evaluated.outcome == ExternalResultPolicy.Outcome.GRANT
                    && store().edit()
                        .putString(ExternalClientContract.EXTRA_LEASE_ID,
                                evaluated.receipt.leaseId)
                        .putLong(ExternalClientContract.EXTRA_EXPIRES_ELAPSED,
                                evaluated.receipt.expiresElapsed)
                        .putString("phase", ThemeClientStatePolicy.ACTIVE).commit()) {
                status.setText("GRANTED: the bounded theme lease is active.");
                refreshButtons(ThemeClientStatePolicy.ACTIVE);
                return;
            }
            if (evaluated.outcome == ExternalResultPolicy.Outcome.CLEAN
                    && store().edit().clear().commit()) {
                status.setText("Not granted: " + decision);
                refreshButtons(ThemeClientStatePolicy.CLEAN);
                return;
            }
        }
        markUnknown("Result is uncertain. Reconcile before requesting again.");
    }

    private void handleRevokeResult(int resultCode, Intent data) {
        try {
            if (data == null || data.getExtras() == null)
                throw new SecurityException("missing result");
            String decision = ExternalResultPolicy.validateDecision(
                    data.getExtras().keySet(),
                    data.getStringExtra(ExternalClientContract.EXTRA_DECISION), false);
            if (ExternalResultPolicy.isConfirmedRevoke(
                    resultCode == RESULT_OK, data.getExtras().keySet(), decision)
                    && store().edit().clear().commit()) {
                status.setText("REVOKED: the previous theme was restored.");
                refreshButtons(ThemeClientStatePolicy.CLEAN);
                return;
            }
        } catch (SecurityException invalid) { }
        markUnknown("Revocation is not confirmed. Reconcile before another request.");
    }

    private void handleReconcileResult(int resultCode, Intent data) {
        if (data != null && data.getExtras() != null) {
            ExternalResultPolicy.Evaluation evaluated =
                    ExternalResultPolicy.evaluateReconcileResult(
                            resultCode == RESULT_CANCELED, data.getExtras().keySet(),
                            data.getStringExtra(ExternalClientContract.EXTRA_DECISION));
            if (evaluated.outcome == ExternalResultPolicy.Outcome.CLEAN
                    && store().edit().clear().commit()) {
                status.setText("Reconciled clean: no active theme lease remains.");
                refreshButtons(ThemeClientStatePolicy.CLEAN);
                return;
            }
        }
        markUnknown("Reconciliation is still uncertain. Do not request another lease.");
    }

    private void markUnknown(String message) {
        store().edit().putString("phase", ThemeClientStatePolicy.UNKNOWN).commit();
        status.setText(message);
        refreshButtons(ThemeClientStatePolicy.UNKNOWN);
    }

    private void refreshState() {
        if (!pairedControllerInstalled()) {
            status.setText("Paired NullGate controller not installed or signer mismatch.");
            refreshButtons(ThemeClientStatePolicy.UNKNOWN);
            return;
        }
        String phase = currentPhase();
        if (ThemeClientStatePolicy.CLEAN.equals(phase)) {
            status.setText("Ready. Choose a palette and style.");
        } else if (ThemeClientStatePolicy.PENDING.equals(phase)
                || ThemeClientStatePolicy.UNKNOWN.equals(phase)) {
            status.setText("An earlier result is unresolved. Reconcile before requesting again.");
        } else {
            long remaining = store().getLong(
                    ExternalClientContract.EXTRA_EXPIRES_ELAPSED, 0L)
                    - SystemClock.elapsedRealtime();
            status.setText(remaining > 0
                    ? "Theme lease active for at most " + ((remaining + 999L) / 1000L)
                        + " seconds."
                    : "Lease deadline passed; reconcile to confirm restoration.");
        }
        refreshButtons(phase);
    }

    private String currentPhase() {
        String leaseId = store().getString(ExternalClientContract.EXTRA_LEASE_ID, null);
        String stored = store().getString("phase", null);
        String phase = ThemeClientStatePolicy.normalize(stored, leaseId != null,
                store().getLong(ExternalClientContract.EXTRA_EXPIRES_ELAPSED, 0L),
                SystemClock.elapsedRealtime());
        if (ThemeClientStatePolicy.UNKNOWN.equals(phase)
                && !ThemeClientStatePolicy.UNKNOWN.equals(stored))
            store().edit().putString("phase", ThemeClientStatePolicy.UNKNOWN).commit();
        return phase;
    }

    private void refreshButtons(String phase) {
        boolean paired = pairedControllerInstalled();
        boolean canRequest = paired && ThemeClientStatePolicy.canRequest(phase);
        palette.setEnabled(canRequest);
        style.setEnabled(canRequest);
        requestButton.setEnabled(canRequest);
        revokeButton.setEnabled(paired && ThemeClientStatePolicy.ACTIVE.equals(phase));
        reconcileButton.setEnabled(paired && ThemeClientStatePolicy.needsReconcile(phase));
    }

    private void disableActions() {
        palette.setEnabled(false);
        style.setEnabled(false);
        requestButton.setEnabled(false);
        revokeButton.setEnabled(false);
        reconcileButton.setEnabled(false);
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

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        spinner.setBackgroundColor(Color.rgb(230, 236, 233));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private Button actionButton(String value, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackgroundColor(color);
        button.setFilterTouchesWhenObscured(true);
        return button;
    }

    private SharedPreferences store() { return getSharedPreferences(STORE, MODE_PRIVATE); }
    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
