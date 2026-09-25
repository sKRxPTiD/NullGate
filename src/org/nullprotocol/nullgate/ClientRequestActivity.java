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
    private static final Object STATE_LOCK = new Object();
    private static final java.util.concurrent.ExecutorService TRANSPORT =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final BrokerClient broker = new BrokerClient();
    private ExternalClientPolicy.ApprovedThemeRequest approved;
    private long approvalGeneration = -1L;
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
            if (ClientRequestContract.ACTION_RECONCILE.equals(getIntent().getAction())) {
                setContentView(buildProgress("Reconciling the client-owned lease…"));
                handleReconcile(getIntent());
                return;
            }
            approved = validateCallerAndPayload(getIntent());
            approvalGeneration = reserveApprovalGeneration();
            if (approvalGeneration < 0) {
                finishDenied("DENIED_RECOVERY_RECORD_FAILED");
                return;
            }
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
        reserveCleanup(requestedId, caller.packageName, caller.uid, "REVOKING");
        TRANSPORT.execute(() -> {
            try {
                String decision = broker.revoke(requestedId).code;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if ("REVOKED".equals(decision)
                            && !clearRecordIfOwned(requestedId, caller.packageName, caller.uid)) {
                        status.setText("Broker cleanup completed, but controller state is unresolved.");
                        return;
                    }
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

    private void handleReconcile(Intent intent) throws Exception {
        Bundle extras = intent.getExtras();
        if (extras == null || !ClientRequestContract.hasExactReconcileKeys(extras.keySet()))
            throw new SecurityException("reconcile payload schema mismatch");
        CallerEvidence caller = callerEvidence();
        ExternalClientPolicy.authorizeClient(
                extras.getInt(ClientRequestContract.EXTRA_PROTOCOL_VERSION, -1), true,
                caller.uid, caller.packageName, caller.versionCode,
                caller.owners, caller.digests, soleSignerDigest(getPackageName()));
        OwnedRecord record = reserveReconciliation(caller.packageName, caller.uid);
        if (record == null) {
            finishDenied("NOT_FOUND");
            return;
        }
        // Reconciliation is deliberately fail-closed: never infer a live broker lease
        // from controller preferences after result loss or process recreation.
        reconcileUnknown(record.leaseId, caller.packageName, caller.uid);
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
                + "\nLease window: " + (approved.durationMillis / 1000L)
                + " seconds of elapsed runtime"
                + "\nRestoration may be delayed by device suspend or a blocked platform call.\n\n"
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
        final OwnedRecord record;
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            if (prefs.getString("lease_id", null) == null) return;
            if (!recordMatchesApproved(prefs)) {
                approveButton.setEnabled(false);
                status.setText("Another external lease is unresolved. Use NullGate recovery.");
                return;
            }
            record = new OwnedRecord(prefs.getString("lease_id", null),
                    prefs.getString("phase", "UNKNOWN"), prefs.getLong("expires", 0L));
        }
        approveButton.setEnabled(false);
        status.setText("Existing state requires fail-closed reconciliation…");
        reconcileUnknown(record.leaseId);
    }

    private void approve() {
        approveButton.setEnabled(false); denyButton.setEnabled(false);
        status.setText("Submitting the typed request to the temporary broker…");
        try {
            ExternalClientPolicy.ApprovedThemeRequest current =
                    validateCallerAndPayload(getIntent());
            if (!sameApproval(approved, current))
                throw new SecurityException("caller changed while approval was open");
        } catch (Exception changed) {
            finishDenied("DENIED_CALLER_CHANGED");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        LeaseRequest lease = LeaseRequest.forSystemTheme(
                approved.seedArgb, approved.style.name(), now, approved.durationMillis);
        if (!saveSubmitting(lease, approvalGeneration)) {
            finishDenied("DENIED_RECOVERY_RECORD_FAILED");
            return;
        }
        TRANSPORT.execute(() -> {
            try {
                String decision = broker.issue(lease).code;
                boolean settled = persistDecision(lease.id, approved.clientPackage,
                        approved.clientUid, approvalGeneration, decision);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!settled) {
                        reconcileUnknown(lease.id, approved.clientPackage, approved.clientUid);
                        return;
                    }
                    if ("GRANTED".equals(decision)) {
                        if (canDeliverGrant(lease.id, approved.clientPackage,
                                approved.clientUid, approvalGeneration,
                                lease.expiresAtElapsedMillis))
                            finishGranted(lease.id, lease.expiresAtElapsedMillis);
                        else reconcileUnknown(lease.id, approved.clientPackage,
                                approved.clientUid);
                    }
                    else if ("CLEANUP_FAILED".equals(decision))
                        showHostRecoveryRequired();
                    else if (clearRecordIfOwned(lease.id, approved.clientPackage,
                            approved.clientUid)) finishDenied(decision);
                    else showHostRecoveryRequired();
                });
            } catch (java.io.IOException unknown) {
                markUnknownIfOwned(lease.id, approved.clientPackage, approved.clientUid);
                runOnUiThread(() -> { if (!isDestroyed()) reconcileUnknown(
                        lease.id, approved.clientPackage, approved.clientUid); });
            }
        });
    }

    private void reconcileUnknown(String leaseId) {
        reconcileUnknown(leaseId, approved.clientPackage, approved.clientUid);
    }

    private void reconcileUnknown(String leaseId, String clientPackage, int clientUid) {
        if (approveButton != null) approveButton.setEnabled(false);
        if (denyButton != null) denyButton.setEnabled(false);
        status.setText("Outcome uncertain; requesting fail-closed revocation…");
        if (!invalidateAndMarkReconciliation(leaseId, clientPackage, clientUid)) {
            showHostRecoveryRequired();
            return;
        }
        TRANSPORT.execute(() -> {
            try {
                String decision = broker.revoke(leaseId).code;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if ("REVOKED".equals(decision) || "NOT_FOUND".equals(decision)) {
                        OwnedRecord current = ownedRecord(clientPackage, clientUid);
                        if (current == null || (leaseId.equals(current.leaseId)
                                && clearRecordIfOwned(leaseId, clientPackage, clientUid)))
                            finishDenied("REVOKED_AFTER_UNCERTAIN_RESULT");
                        else showHostRecoveryRequired();
                    } else showHostRecoveryRequired();
                });
            } catch (java.io.IOException failed) {
                runOnUiThread(() -> { if (!isDestroyed()) showHostRecoveryRequired(); });
            }
        });
    }

    private boolean saveSubmitting(LeaseRequest lease, long generation) {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            if (!ExternalLeaseStatePolicy.canReserveSubmission(
                    prefs.getString("lease_id", null) != null, generation,
                    prefs.getLong("approval_generation", 0L))) return false;
            return prefs.edit().putString("lease_id", lease.id)
                    .putString("lease_nonce", lease.nonce)
                    .putString("client_package", approved.clientPackage)
                    .putInt("client_uid", approved.clientUid)
                    .putInt("seed", approved.seedArgb).putString("style", approved.style.name())
                    .putLong("duration", approved.durationMillis)
                    .putLong("issued", lease.issuedAtElapsedMillis)
                    .putLong("expires", lease.expiresAtElapsedMillis)
                    .putString("phase", "SUBMITTING").commit();
        }
    }

    private boolean recordMatchesApproved(SharedPreferences prefs) {
        return approved.clientPackage.equals(prefs.getString("client_package", ""))
                && approved.clientUid == prefs.getInt("client_uid", -1)
                && approved.seedArgb == prefs.getInt("seed", 0)
                && approved.style.name().equals(prefs.getString("style", ""))
                && approved.durationMillis == prefs.getLong("duration", -1);
    }

    private boolean persistDecision(String leaseId, String clientPackage, int clientUid,
            long generation, String decision) {
        String phase = "GRANTED".equals(decision) ? "ACTIVE"
                : "CLEANUP_FAILED".equals(decision) ? "CLEANUP_FAILED" : "FINISHED";
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            return recordOwnedBy(prefs, leaseId, clientPackage, clientUid)
                    && ExternalLeaseStatePolicy.canSettle(leaseId,
                        prefs.getString("lease_id", null), prefs.getString("phase", ""),
                        generation, prefs.getLong("approval_generation", -1L))
                    && prefs.edit().putString("phase", phase)
                        .putString("decision", decision).commit();
        }
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
        if (approveButton != null) approveButton.setEnabled(false);
        if (denyButton != null) denyButton.setEnabled(false);
        status.setText("Cleanup is unconfirmed. Keep PiXi connected and use NullGate host recovery.");
    }

    private void markUnknownIfOwned(String leaseId, String clientPackage, int clientUid) {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            if (recordOwnedBy(prefs, leaseId, clientPackage, clientUid))
                prefs.edit().putString("phase", "UNKNOWN").commit();
        }
    }

    private boolean clearRecordIfOwned(String leaseId, String clientPackage, int clientUid) {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            if (!recordOwnedBy(prefs, leaseId, clientPackage, clientUid)) return false;
            long generation = prefs.getLong("approval_generation", 0L);
            return prefs.edit().clear().putLong("approval_generation", generation).commit();
        }
    }

    private long reserveApprovalGeneration() {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            long next = nextGeneration(prefs.getLong("approval_generation", 0L));
            return prefs.edit().putLong("approval_generation", next).commit() ? next : -1L;
        }
    }

    private void reserveCleanup(String leaseId, String clientPackage, int clientUid,
            String phase) {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            if (!recordOwnedBy(prefs, leaseId, clientPackage, clientUid))
                throw new SecurityException("cleanup does not own the recorded lease");
            long next = nextGeneration(prefs.getLong("approval_generation", 0L));
            if (!prefs.edit().putLong("approval_generation", next)
                    .putString("phase", phase).commit())
                throw new SecurityException("cleanup reservation could not be persisted");
        }
    }

    private OwnedRecord reserveReconciliation(String clientPackage, int clientUid) {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            String leaseId = prefs.getString("lease_id", null);
            long next = nextGeneration(prefs.getLong("approval_generation", 0L));
            SharedPreferences.Editor edit = prefs.edit().putLong("approval_generation", next);
            if (leaseId == null) {
                if (!edit.commit())
                    throw new SecurityException("reconciliation invalidation failed");
                return null;
            }
            if (!recordOwnedBy(prefs, leaseId, clientPackage, clientUid))
                throw new SecurityException("reconciliation does not own the recorded lease");
            if (!edit.putString("phase", "RECONCILING").commit())
                throw new SecurityException("reconciliation reservation failed");
            return new OwnedRecord(leaseId, "RECONCILING", prefs.getLong("expires", 0L));
        }
    }

    private boolean invalidateAndMarkReconciliation(String leaseId, String clientPackage,
            int clientUid) {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            long next = nextGeneration(prefs.getLong("approval_generation", 0L));
            SharedPreferences.Editor edit = prefs.edit().putLong("approval_generation", next);
            if (recordOwnedBy(prefs, leaseId, clientPackage, clientUid))
                edit.putString("phase", "RECONCILING");
            return edit.commit();
        }
    }

    private boolean canDeliverGrant(String leaseId, String clientPackage, int clientUid,
            long generation, long expires) {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            return recordOwnedBy(prefs, leaseId, clientPackage, clientUid)
                    && ExternalLeaseStatePolicy.canDeliverGrant(leaseId,
                        prefs.getString("lease_id", null), prefs.getString("phase", ""),
                        generation, prefs.getLong("approval_generation", -1L),
                        expires, prefs.getLong("expires", -1L),
                        SystemClock.elapsedRealtime());
        }
    }

    private static long nextGeneration(long current) {
        return current == Long.MAX_VALUE ? 1L : current + 1L;
    }

    private OwnedRecord ownedRecord(String clientPackage, int clientUid) {
        synchronized (STATE_LOCK) {
            SharedPreferences prefs = store();
            String leaseId = prefs.getString("lease_id", null);
            if (!recordOwnedBy(prefs, leaseId, clientPackage, clientUid)) return null;
            return new OwnedRecord(leaseId, prefs.getString("phase", "UNKNOWN"),
                    prefs.getLong("expires", 0L));
        }
    }

    private static boolean recordOwnedBy(SharedPreferences prefs, String leaseId,
            String clientPackage, int clientUid) {
        return ExternalLeaseStatePolicy.owns(prefs.getString("lease_id", null),
                prefs.getString("client_package", ""), prefs.getInt("client_uid", -1),
                leaseId, clientPackage, clientUid);
    }

    private static boolean sameApproval(ExternalClientPolicy.ApprovedThemeRequest left,
            ExternalClientPolicy.ApprovedThemeRequest right) {
        return left.clientUid == right.clientUid
                && left.seedArgb == right.seedArgb
                && left.durationMillis == right.durationMillis
                && left.clientPackage.equals(right.clientPackage)
                && left.style == right.style;
    }

    private static final class OwnedRecord {
        final String leaseId; final String phase; final long expires;
        OwnedRecord(String leaseId, String phase, long expires) {
            this.leaseId = leaseId; this.phase = phase; this.expires = expires;
        }
    }
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
