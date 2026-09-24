package org.nullprotocol.nullgate.broker;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;

/** Harmless root-side lifecycle proof. It can only create and remove one bounded marker file. */
public final class EphemeralMarkerAdapter implements CapabilityAdapter {
    private static final String CONTROLLER = "org.nullprotocol.nullgate";
    private static final File DIRECTORY = new File("/data/local/tmp/nullgate/leases");

    private final java.util.Set<String> createdPaths = new java.util.HashSet<>();

    @Override public boolean isReady(String targetPackage) {
        try {
            RuntimeFiles.requirePrivateDirectory(RuntimeFiles.ROOT);
            return CONTROLLER.equals(targetPackage);
        } catch (Exception unsafe) { return false; }
    }

    @Override public void activate(LeaseEnvelope lease) throws Exception {
        EphemeralMarkerRecord.requireSelfTestLease(lease);
        RuntimeFiles.requirePrivateDirectory(RuntimeFiles.ROOT);
        ensureDirectory();
        String path = markerFor(lease).getAbsolutePath();
        FileDescriptor descriptor = null;
        boolean created = false;
        try {
            descriptor = Os.open(path, OsConstants.O_WRONLY | OsConstants.O_CREAT
                    | OsConstants.O_EXCL | OsConstants.O_NOFOLLOW | OsConstants.O_CLOEXEC, 0600);
            created = true;
            createdPaths.add(path);
            byte[] content = EphemeralMarkerRecord.content(lease);
            int offset = 0;
            while (offset < content.length) {
                int written = Os.write(descriptor, content, offset, content.length - offset);
                if (written <= 0) throw new IllegalStateException("short marker write");
                offset += written;
            }
            Os.fsync(descriptor);
        } catch (Exception failure) {
            if (created) {
                try { Os.remove(path); createdPaths.remove(path); } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        } finally {
            if (descriptor != null) {
                try { Os.close(descriptor); } catch (Exception ignored) { }
            }
        }
    }

    @Override public void deactivate(LeaseEnvelope lease) throws Exception {
        EphemeralMarkerRecord.requireSelfTestLease(lease);
        String path = markerFor(lease).getAbsolutePath();
        if (!createdPaths.contains(path)) return; // Never remove a file this instance did not create.
        RuntimeFiles.requirePrivateDirectory(RuntimeFiles.ROOT);
        RuntimeFiles.requirePrivateDirectory(DIRECTORY.getAbsolutePath());
        try {
            Os.remove(path);
        } catch (ErrnoException error) {
            if (error.errno != OsConstants.ENOENT) throw error;
        }
        createdPaths.remove(path);
    }

    private static void ensureDirectory() throws Exception {
        if (!DIRECTORY.isDirectory()) {
            try { Os.mkdir(DIRECTORY.getAbsolutePath(), 0700); }
            catch (ErrnoException error) {
                if (error.errno != OsConstants.EEXIST) throw error;
            }
        }
        RuntimeFiles.requirePrivateDirectory(DIRECTORY.getAbsolutePath());
    }

    private static File markerFor(LeaseEnvelope lease) {
        return new File(DIRECTORY, EphemeralMarkerRecord.fileName(lease));
    }
}
