package org.bartram.myfeeder.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedUrlValidatorTest {

    /** Builds a validator whose DNS resolution is stubbed to the given literal IP. */
    private FeedUrlValidator validatorResolvingTo(String ip) {
        return new FeedUrlValidator(host -> new InetAddress[]{InetAddress.getByName(ip)});
    }

    @Test
    void allowsPublicHttpsUrl() {
        FeedUrlValidator validator = validatorResolvingTo("93.184.216.34"); // public
        assertThatCode(() -> validator.validate("https://example.com/feed")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonHttpScheme() {
        FeedUrlValidator validator = validatorResolvingTo("93.184.216.34");
        assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    @Test
    void rejectsLoopbackAddress() {
        FeedUrlValidator validator = validatorResolvingTo("127.0.0.1");
        assertThatThrownBy(() -> validator.validate("http://localhost/feed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void rejectsPrivateRfc1918Address() {
        FeedUrlValidator validator = validatorResolvingTo("10.0.0.5");
        assertThatThrownBy(() -> validator.validate("http://internal.example/feed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void rejectsLinkLocalMetadataAddress() {
        FeedUrlValidator validator = validatorResolvingTo("169.254.169.254");
        assertThatThrownBy(() -> validator.validate("http://metadata/latest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void rejectsIpv6UniqueLocalAddress() {
        FeedUrlValidator validator = validatorResolvingTo("fd00::1"); // fc00::/7 ULA
        assertThatThrownBy(() -> validator.validate("http://ula.example/feed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void rejectsCarrierGradeNatAddress() {
        FeedUrlValidator validator = validatorResolvingTo("100.64.0.1"); // 100.64.0.0/10 CGNAT
        assertThatThrownBy(() -> validator.validate("http://cgnat.example/feed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void rejectsBroadcastAddress() {
        FeedUrlValidator validator = validatorResolvingTo("255.255.255.255");
        assertThatThrownBy(() -> validator.validate("http://broadcast.example/feed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void rejectsIpv4MappedLoopback() {
        FeedUrlValidator validator = validatorResolvingTo("::ffff:127.0.0.1");
        assertThatThrownBy(() -> validator.validate("http://mapped.example/feed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void allowsPublicIpv6Address() {
        FeedUrlValidator validator = validatorResolvingTo("2606:4700:4700::1111"); // Cloudflare DNS
        assertThatCode(() -> validator.validate("https://ipv6.example/feed")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedUrl() {
        FeedUrlValidator validator = validatorResolvingTo("93.184.216.34");
        assertThatThrownBy(() -> validator.validate("http://exa mple.com/feed"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
