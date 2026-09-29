package org.nullprotocol.nullgate;

import org.nullprotocol.nullgate.protocol.RootSessionProtocol;

/** Candidate checks run this under run-as; the broker still authenticates the actual app UID. */
public final class RootControlMain {
    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("expected operation and optional target");
            RootSessionProtocol.Response result = new RootBrokerClient().exchange(
                    RootSessionProtocol.Operation.valueOf(args[0]), args.length == 2 ? args[1] : "");
            System.out.println(result.code + " " + result.state + " " + result.target + " shells=" + result.shellCount);
            System.exit(result.code == RootSessionProtocol.Code.OK ? 0 : 1);
        } catch (Exception failure) { System.err.println("Root control unavailable: " + failure.getClass().getSimpleName()); System.exit(2); }
    }
    private RootControlMain() { }
}
