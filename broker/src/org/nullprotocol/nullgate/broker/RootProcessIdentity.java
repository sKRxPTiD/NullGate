package org.nullprotocol.nullgate.broker;

/** Linux /proc stat fields used to keep signals confined to a session's process group. */
public final class RootProcessIdentity {
    public final int pid, parent, group, session;
    public final long started;
    private RootProcessIdentity(int pid, int parent, int group, int session, long started) {
        this.pid = pid; this.parent = parent; this.group = group; this.session = session; this.started = started;
    }
    public static RootProcessIdentity parse(String stat) {
        int open = stat.indexOf('('), close = stat.lastIndexOf(')');
        if (open < 1 || close <= open) throw new IllegalArgumentException("invalid process stat");
        int pid = Integer.parseInt(stat.substring(0, open).trim());
        String[] fields = stat.substring(close + 1).trim().split("\\s+");
        if (pid < 1 || fields.length < 20) throw new IllegalArgumentException("invalid process identity");
        return new RootProcessIdentity(pid, Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Long.parseLong(fields[19]));
    }
}
