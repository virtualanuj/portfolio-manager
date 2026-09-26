package com.portfoliomanager.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

class LoopbackGuardTest {

    private static LoopbackGuard guardBoundTo(String host) throws UnknownHostException {
        ServerProperties properties = new ServerProperties();
        if (host != null) {
            properties.setAddress(InetAddress.getByName(host));
        }
        return new LoopbackGuard(properties);
    }

    @Test
    void wildcardAddressIsRejected() {
        assertThatThrownBy(() -> guardBoundTo("0.0.0.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void missingAddressIsRejected() {
        assertThatThrownBy(() -> guardBoundTo(null)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "::1", "localhost"})
    void loopbackAddressesPass(String host) {
        assertThatCode(() -> guardBoundTo(host)).doesNotThrowAnyException();
    }
}
