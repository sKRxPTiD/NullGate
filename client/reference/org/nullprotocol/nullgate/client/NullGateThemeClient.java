package org.nullprotocol.nullgate.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.security.MessageDigest;

/** Minimal reference client. It requests typed effects and never receives root. */
public final class NullGateThemeClient {
    public static final String NULLGATE_PACKAGE = "org.nullprotocol.nullgate";
    public static final String NULLGATE_ACTIVITY =
            "org.nullprotocol.nullgate.ClientRequestActivity";
    public static final String NULLGATE_SIGNER =
            "fc122f22e4716ba03cae81893783e437553d4a3ad21e897f4efe3367c415bafe";
    public static final String ACTION_REQUEST =
            "org.nullprotocol.nullgate.action.REQUEST_THEME_LEASE";
    public static final String ACTION_REVOKE =
            "org.nullprotocol.nullgate.action.REVOKE_LEASE";
    public static final String EXTRA_DECISION = "decision";
    public static final String EXTRA_LEASE_ID = "leaseId";

    private NullGateThemeClient() { }

    public static boolean trustedControllerInstalled(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    NULLGATE_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) return false;
            Signature[] signers = info.signingInfo.getApkContentsSigners();
            if (signers == null || signers.length != 1) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signers[0].toByteArray());
            StringBuilder value = new StringBuilder(64);
            for (byte part : digest) value.append(String.format("%02x", part & 0xff));
            return NULLGATE_SIGNER.equals(value.toString());
        } catch (Exception absentOrUntrusted) {
            return false;
        }
    }

    public static void requestTheme(Activity activity, int requestCode, int seedArgb,
            String themeStyle, long durationMillis) {
        if (!trustedControllerInstalled(activity))
            throw new SecurityException("trusted NullGate controller is unavailable");
        Intent request = explicit(ACTION_REQUEST)
                .putExtra("protocolVersion", 1)
                .putExtra("seedArgb", seedArgb)
                .putExtra("themeStyle", themeStyle)
                .putExtra("durationMillis", durationMillis);
        activity.startActivityForResult(request, requestCode);
    }

    public static void revoke(Activity activity, int requestCode, String leaseId) {
        if (!trustedControllerInstalled(activity))
            throw new SecurityException("trusted NullGate controller is unavailable");
        if (leaseId == null || leaseId.isEmpty()) throw new IllegalArgumentException("leaseId");
        activity.startActivityForResult(explicit(ACTION_REVOKE)
                .putExtra("protocolVersion", 1)
                .putExtra("leaseId", leaseId), requestCode);
    }

    private static Intent explicit(String action) {
        return new Intent(action).setComponent(
                new ComponentName(NULLGATE_PACKAGE, NULLGATE_ACTIVITY));
    }
}
