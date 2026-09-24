package org.nullprotocol.nullgate.broker;

import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.FileDescriptor;

/** Refuses precreated symlinks and unsafe leaf permissions. Root/ADB-shell are trusted operators. */
final class RuntimeFiles {
    static final String ROOT = "/data/local/tmp/nullgate";
    static void requirePrivateDirectory(String path) throws Exception {
        StructStat stat = Os.lstat(path);
        if (!OsConstants.S_ISDIR(stat.st_mode) || stat.st_uid != 0
                || (stat.st_mode & 0777) != 0700)
            throw new SecurityException("runtime must be a real root-owned 0700 directory");
    }
    static void writeExclusive(String path, byte[] content) throws Exception {
        FileDescriptor fd = Os.open(path, OsConstants.O_WRONLY | OsConstants.O_CREAT
                | OsConstants.O_EXCL | OsConstants.O_NOFOLLOW | OsConstants.O_CLOEXEC, 0600);
        try {
            int offset = 0;
            while (offset < content.length) {
                int n = Os.write(fd, content, offset, content.length - offset);
                if (n <= 0) throw new IllegalStateException("short runtime write");
                offset += n;
            }
            Os.fsync(fd);
        } finally { Os.close(fd); }
    }
}
