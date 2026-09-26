package com.portfoliomanager.config;

import java.net.InetAddress;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails startup under the {@code local} profile unless the server is bound to a loopback address,
 * so the unauthenticated local API cannot be exposed by accident. A missing {@code server.address}
 * means "all interfaces" and is therefore rejected.
 */
@Component
@Profile("local")
public class LoopbackGuard {

    public LoopbackGuard(ServerProperties serverProperties) {
        InetAddress address = serverProperties.getAddress();
        if (address == null || !address.isLoopbackAddress()) {
            throw new IllegalStateException(
                    "Profile 'local' requires server.address to be a loopback address, but was: "
                            + address);
        }
    }
}
