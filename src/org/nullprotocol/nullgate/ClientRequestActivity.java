package org.nullprotocol.nullgate;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
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

/** User-confirmed, typed app-to-controller entry point. It never exposes the root broker. */
public final class ClientRequestActivity extends Activity {
    private static final String STORE = "external_client_leases";
    private static final java.util.concurrent.ExecutorService TRANSPORT =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final BrokerClient broker = new BrokerClient();
    private ExternalClientPolicy.ApprovedThemeRequest approved;
    private TextView status;
    private Button approveButton;
    private Button denyButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (android.os.Build.VERSION.SDK_INT >= 31) getWindow().setHideOverlayWindows(true);
        try {
            if (ClientRequestContract.ACTION_REVOKE.equals(getIntent().getAction())) {
                setContentView(buildProgress("Verifying and revoking the client-owned lease…"));
                handleRevoke(getIntent());
                return;
            }
            approved = validateCallerAndPayload(getIntent());
            setContentView(buildConfirmation());
            recoverExistingOrAwaitApproval();
        } catch (Exception denied) {
            finishDenied("DENIED_INVALID_CLIENT_REQUEST");
        }
    }

    private static final class CallerEvidence {
        final String packageName; final int uid; final long versionCode;
        final String[] owners; final String[] digests;
        CallerEvidence(String packageName, int uid, long versionCode,
                String[] owners, String[] digests) {
            this.packageName = packageName; this.uid = uid;
            this.versionCode = versionCode;
            this.owners = owners; this.digests = digests;
        }
    }

    private CallerEvidence callerEvidence() throws Exception {
        String caller = getCallingPackage();
        if (caller == null || getCallingActivity() == null
                || !caller.equals(getCallingActivity().getPackageName()))
            throw new SecurityException("result-bound caller required");
        PackageManager packages = getPackageManager();
        ApplicationInfo application = packages.getApplicationInfo(caller, 0);
        String[] owners = packages.getPackagesForUid(application.uid);
        PackageInfo packageInfo = packages.getPackageInfo(
                caller, PackageManager.GET_SIGNING_CERTIFICATES);
        if (packageInfo.signingInfo == null) throw new SecurityException("signer unavailable");
        Signature[] signatures = packageInfo.signingInfo.getApkContentsSigners();
        String[] digests = new String[signatures == null ? 0 : signatures.length];
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < digests.length; i++) {
            byte[] digest = sha256.digest(signatures[i].toByteArray());
            StringBuilder text = new StringBuilder(64);
            for (byte value : digest) text.append(String.format("%02x", value & 0xff));
            digests[i] = text.toString();
        }
        return new CallerEvidence(caller, application.uid, packageInfo.getLongVersionCode(),
                owners, digests);
    }

    private ExternalClientPolicy.ApprovedThemeRequest validateCallerAndPayload(Intent intent)
            throws Exception {
        if (intent == null || !ClientRequestContract.ACTION_REQUEST_THEME.equals(intent.getAction()))
            throw new SecurityException("unsupported client action");
        Bundle extras = intent.getExtras();
        if (extras == null || !ClientRequestContract.hasExactRequestKeys(extras.keySet()))
            throw new SecurityException("client payload schema mismatch");
        CallerEvidence caller = callerEvidence();
        return ExternalClientPolicy.authorizeThemeRequest(
                extras.getInt(ClientRequestContract.EXTRA_PROTOCOL_VERSION, -1),
                true, caller.uid, caller.packageName, caller.versionCode,
                caller.owners, caller.digests, soleSignerDigest(getPackageName()),
                extras.getInt(ClientRequestContract.EXTRA_SEED_ARGB, 0),
                extras.getString(ClientRequestContract.EXTRA_THEME_STYLE),
                extras.getLong(ClientRequestContract.EXTRA_DURATION_MILLIS, -1));
    }

    private void handleRevoke(Intent intent) throws Exception {
        Bundle extras = intent.getExtras();
        if (extras == null || !ClientRequestContract.hasExactRevokeKeys(extras.keySet()))
            throw new SecurityException("revoke payload schema mismatch");
        CallerEvidence caller = callerEvidence();
        ExternalClientPolicy.authorizeClient(
                extras.getInt(ClientRequestContract.EXTRA_PROTOCOL_VERSION, -1), true,
                caller.uid, caller.packageName, caller.versionCode,
                caller.owners, caller.digests, soleSignerDigest(getPackageName()));
        String requestedId = extras.getString(ClientRequestContract.EXTRA_LEASE_ID);
        SharedPreferences prefs = store();
        String recordedId = prefs.getString("lease_id", null);
        if (requestedId == null || !requestedId.equals(recordedId)
                || caller.uid != prefs.getInt("client_uid", -1)
                || !caller.packageName.equals(prefs.getString("client_package", "")))
            throw new SecurityException("revoke does not own the recorded lease");
        TRANSPORT.execute(() -> {
            try {
                String decision = broker.revoke(requestedId).code;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if ("REVOKED".equals(decision)) clearRecord();
                    Intent result = new Intent().putExtra(
                            ClientRequestContract.EXTRA_DECISION, decision);
                    setResult("REVOKED".equals(decision) ? RESULT_OK : RESULT_CANCELED, result);
                    finish();
                });
            } catch (java.io.IOException failed) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    status.setText("Revocation is unconfirmed. Keep PiXi connected and use host recovery.");
                });
            }
        });
    }

    private View buildConfirmation() {
        int pad = dp(24);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(pad, dp(48), pad, dp(48));
        page.setBackgroundColor(Color.rgb(230, 214, 193));

        TextView mark = label("∅ NULLGATE", 27, Color.rgb(0, 104, 61));
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(mark);
        TextView title = label("Temporary theme request", 21, Color.rgb(74, 37, 25));
        title.setPadding(0, dp(22), 0, dp(12));
        page.addView(title);
        TextView details = label(
                clientName() + " requests a temporary native theme lease.\n\n"
                + "Seed: #" + String.format("%06X", approved.seedArgb & 0xffffff)
                + "\nStyle: " + approved.style.name()
                + "\nHard expiration: " + (approved.durationMillis / 1000L) + " seconds\n\n"
                + clientName() + " receives no root shell or privileged process.",
                16, Color.rgb(89, 53, 36));
        page.addView(details, new LinearLayout.LayoutParams(-1, 0, 1));
        status = label("Review the request before approving.", 13, Color.rgb(116, 78, 55));
        status.setPadding(0, dp(12), 0, dp(12));
        page.addView(status);

        approveButton = actionButton("Approve temporary lease", Color.rgb(0, 104, 61));
        approveButton.setFilterTouchesWhenObscured(true);
        approveButton.setOnClickListener(v -> approve());
        page.addView(approveButton, new LinearLayout.LayoutParams(-1, dp(54)));
        denyButton = actionButton("Deny", Color.rgb(103, 70, 50));
        denyButton.setFilterTouchesWhenObscured(true);
        denyButton.setOnClickListener(v -> finishDenied("DENIED_BY_USER"));
        LinearLayout.LayoutParams denyParams = new LinearLayout.LayoutParams(-1, dp(50));
        denyParams.setMargins(0, dp(12), 0, 0);
        page.addView(denyButton, denyParams);
        return page;
    }

    private View buildProgress(String message) {
        LinearLayout page = new LinearLayout(this);
        page.setGravity(Gravity.CENTER); page.setPadding(dp(28), dp(48), dp(28), dp(48));
        page.setBackgroundColor(Color.rgb(230, 214, 193));
        status = label(message, 17, Color.rgb(74, 37, 25));
        page.addView(status); return page;
    }

    private void recoverExistingOrAwaitApproval() {
        SharedPreferences prefs = store();
        String leaseId = prefs.getString("lease_id", null);
        if (leaseId == null) return;
        if (!recordMatchesApproved(prefs)) {
            approveButton.setEnabled(false);
            status.setText("Another external lease is unresolved. Use NullGate recovery.");
            return;
        }
        String phase = prefs.getString("phase", "UNKNOWN");
        if ("ACTIVE".equals(phase)) {
            if (SystemClock.elapsedRealtime() >= prefs.getLong("expires", 0)) {
                approveButton.setEnabled(false);
                status.setText("Prior lease expired; verifying cleanup before another request…");
                reconcileUnknown(leaseId);
                return;
            }
            finishGranted(leaseId, prefs.getLong("expires", 0));
            return;
        }
        approveButton.setEnabled(false);
        status.setText("Recovering the in-flight NullGate decision…");
        status.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            String settled = store().getString("phase", "UNKNOWN");
            if ("ACTIVE".equals(settled))
                finishGranted(leaseId, store().getLong("expires", 0));
            else reconcileUnknown(leaseId);
        }, 2500L);
    }

    private void approve() {
        approveButton.setEnabled(false); denyButton.setEnabled(false);
        status.setText("Submitting the typed request to the temporary broker…");
        long now = SystemClock.elapsedRealtime();
        LeaseRequest lease = LeaseRequest.forSystemTheme(
                approved.seedArgb, approved.style.name(), now, approved.durationMillis);
        if (!saveSubmitting(lease)) {
            finishDenied("DENIED_RECOVERY_RECORD_FAILED");
            return;
        }
        TRANSPORT.execute(() -> {
            try {
                String decision = broker.issue(lease).code;
                persistDecision(decision);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if ("GRANTED".equals(decision))
                        finishGranted(lease.id, lease.expiresAtElapsedMillis);
                    else if ("CLEANUP_FAILED".equals(decision))
                        showHostRecoveryRequired();
                    else finishDenied(decision);
                });
            } catch (java.io.IOException unknown) {
                store().edit().putString("phase", "UNKNOWN").commit();
                runOnUiThread(() -> { if (!isDestroyed()) reconcileUnknown(lease.id); });
            }
        });
    }

    private void reconcileUnknown(String leaseId) {
        approveButton.setEnabled(false); denyButton.setEnabled(false);
        status.setText("Outcome uncertain; requesting fail-closed revocation…");
        TRANSPORT.execute(() -> {
            try {
                String decision = broker.revoke(leaseId).code;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if ("REVOKED".equals(decision)) {
                        clearRecord(); finishDenied("REVOKED_AFTER_UNCERTAIN_RESULT");
                    } else showHostRecoveryRequired();
                });
            } catch (java.io.IOException failed) {
                runOnUiThread(() -> { if (!isDestroyed()) showHostRecoveryRequired(); });
            }
        });
    }

    private boolean saveSubmitting(LeaseRequest lease) {
        if (store().getString("lease_id", null) != null) return false;
        return store().edit().putString("lease_id", lease.id)
                .putString("lease_nonce", lease.nonce)
                .putString("client_package", approved.clientPackage)
                .putInt("client_uid", approved.clientUid)
                .putInt("seed", approved.seedArgb).putString("style", approved.style.name())
                .putLong("duration", approved.durationMillis)
                .putLong("issued", lease.issuedAtElapsedMillis)
                .putLong("expires", lease.expiresAtElapsedMillis)
                .putString("phase", "SUBMITTING").commit();
    }

    private boolean recordMatchesApproved(SharedPreferences prefs) {
        return approved.clientPackage.equals(prefs.getString("client_package", ""))
                && approved.clientUid == prefs.getInt("client_uid", -1)
                && approved.seedArgb == prefs.getInt("seed", 0)
                && approved.style.name().equals(prefs.getString("style", ""))
                && approved.durationMillis == prefs.getLong("duration", -1);
    }

    private void persistDecision(String decision) {
        String phase = "GRANTED".equals(decision) ? "ACTIVE"
                : "CLEANUP_FAILED".equals(decision) ? "CLEANUP_FAILED" : "FINISHED";
        store().edit().putString("phase", phase).putString("decision", decision).commit();
    }

    private void finishGranted(String leaseId, long expires) {
        Intent result = new Intent().putExtra(ClientRequestContract.EXTRA_DECISION, "GRANTED")
                .putExtra(ClientRequestContract.EXTRA_LEASE_ID, leaseId)
                .putExtra(ClientRequestContract.EXTRA_EXPIRES_ELAPSED, expires);
        setResult(RESULT_OK, result); finish();
    }

    private void finishDenied(String decision) {
        Intent result = new Intent().putExtra(ClientRequestContract.EXTRA_DECISION, decision);
        setResult(RESULT_CANCELED, result); finish();
    }

    private void showHostRecoveryRequired() {
        approveButton.setEnabled(false); denyButton.setEnabled(false);
        status.setText("Cleanup is unconfirmed. Keep PiXi connected and use NullGate host recovery.");
    }

    private void clearRecord() { store().edit().clear().commit(); }
    private SharedPreferences store() { return getSharedPreferences(STORE, MODE_PRIVATE); }

    private String clientName() {
        return ExternalClientPolicy.TEST_CLIENT_PACKAGE.equals(approved.clientPackage)
                ? "NullGate Test Client" : "ColorBlendr";
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

    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size);
        text.setTextColor(color); return text;
    }

    private Button actionButton(String value, int color) {
        Button button = new Button(this); button.setText(value); button.setAllCaps(false);
        button.setTextColor(Color.WHITE); button.setTextSize(16);
        button.setBackgroundColor(color); return button;
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
