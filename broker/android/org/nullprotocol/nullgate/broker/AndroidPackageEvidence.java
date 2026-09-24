package org.nullprotocol.nullgate.broker;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.lang.reflect.Method;
import java.security.MessageDigest;

/** Android package-manager evidence collected inside the rooted app_process broker. */
public final class AndroidPackageEvidence implements VerifiedCallerResolver.PackageEvidence {
    private final Context context;
    private final PackageManager packages;

    public AndroidPackageEvidence() throws Exception {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepare();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThread.getDeclaredMethod("systemMain");
        Object thread = systemMain.invoke(null);
        Method getSystemContext = activityThread.getDeclaredMethod("getSystemContext");
        context = (Context) getSystemContext.invoke(thread);
        packages = context.getPackageManager();
    }

    Context context() { return context; }

    @Override public String[] packagesForUid(int uid) {
        return packages.getPackagesForUid(uid);
    }

    public boolean isInstalled(String packageName) {
        try {
            packages.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }

    @Override public String[] currentSignerSha256(String packageName) throws Exception {
        PackageInfo info = packages.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        if (info.signingInfo == null) throw new SecurityException("no signing evidence");
        Signature[] signatures = info.signingInfo.getApkContentsSigners();
        String[] digests = new String[signatures.length];
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < signatures.length; i++) {
            byte[] digest = sha256.digest(signatures[i].toByteArray());
            StringBuilder text = new StringBuilder(64);
            for (byte value : digest) text.append(String.format("%02x", value & 0xff));
            digests[i] = text.toString();
        }
        return digests;
    }
}
