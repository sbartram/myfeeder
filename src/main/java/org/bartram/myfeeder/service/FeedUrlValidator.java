package org.bartram.myfeeder.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Guards outbound feed fetches against SSRF. A feed URL is caller-supplied, so before the server
 * dereferences it this validator requires an http/https scheme and rejects any host that resolves
 * to a non-public address (loopback, link-local, RFC1918 site-local, any-local, or multicast).
 * That keeps a subscribed feed from steering the server at internal targets such as the database,
 * in-cluster services, or a cloud metadata endpoint.
 *
 * <p>Known limitations (accepted for the single-user LAN deployment; revisit if this ever faces
 * untrusted networks):
 * <ul>
 *   <li>DNS rebinding / TOCTOU: this resolves the host and checks the IP, but {@code RestClient}
 *       resolves again when it connects. A host whose DNS flips between the two lookups can pass
 *       validation yet connect to a private address. Closing this requires pinning the connection
 *       to the validated IP (custom resolver / socket factory), not done here.</li>
 *   <li>Redirects are not re-validated per hop — a permitted URL may redirect to a blocked one.</li>
 * </ul>
 */
@Component
public class FeedUrlValidator {

    /** Resolves a hostname to its addresses; injectable so tests need not depend on live DNS. */
    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final HostResolver resolver;

    public FeedUrlValidator() {
        this(InetAddress::getAllByName);
    }

    FeedUrlValidator(HostResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Validates that {@code url} is safe to fetch.
     *
     * @throws IllegalArgumentException if the URL is malformed, is not http/https, has no host, or
     *         resolves to a non-public address. Mapped to HTTP 400 by GlobalExceptionHandler.
     */
    public void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed feed URL: " + url, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException("Feed URL must use http or https: " + url);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Feed URL has no host: " + url);
        }

        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve feed host: " + host, e);
        }
        for (InetAddress address : addresses) {
            if (isNonPublic(address)) {
                throw new IllegalArgumentException(
                        "Feed URL resolves to a non-public address (" + address.getHostAddress() + "): " + url);
            }
        }
    }

    private boolean isNonPublic(InetAddress address) {
        // Covers loopback, IPv6 fec0::/10 site-local, IPv4 RFC1918, 0.0.0.0, and multicast.
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isBlockedIpv4(bytes);
        }
        // fc00::/7 unique local addresses — NOT covered by isSiteLocalAddress (that is fec0::/10).
        if ((bytes[0] & 0xFE) == 0xFC) {
            return true;
        }
        // ::ffff:a.b.c.d IPv4-mapped — re-check the embedded IPv4 (belt-and-suspenders; the JDK
        // normally normalizes these to Inet4Address, but a custom resolver may not).
        if (isIpv4Mapped(bytes)) {
            try {
                return isNonPublic(InetAddress.getByAddress(
                        new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]}));
            } catch (UnknownHostException e) {
                return true; // fail closed
            }
        }
        return false;
    }

    private boolean isBlockedIpv4(byte[] b) {
        int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
        // 100.64.0.0/10 carrier-grade NAT (RFC 6598) — not RFC1918, so isSiteLocalAddress misses it.
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;
        }
        // 255.255.255.255 limited broadcast.
        return b0 == 255 && b1 == 255 && (b[2] & 0xFF) == 255 && (b[3] & 0xFF) == 255;
    }

    private boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
    }
}
