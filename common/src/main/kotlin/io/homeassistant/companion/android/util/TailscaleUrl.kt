package io.homeassistant.companion.android.util

import okhttp3.HttpUrl

private const val TAILSCALE_MAGIC_DNS_SUFFIX = ".ts.net"
private const val TAILSCALE_IPV6_PREFIX = "fd7a:115c:a1e0:"
private const val IPV4_OCTET_COUNT = 4
private const val TAILSCALE_IPV4_FIRST_OCTET = 100
private val TAILSCALE_IPV4_SECOND_OCTET = 64..127
private val IPV4_OCTET_RANGE = 0..255

/**
 * Whether the host of this URL is an address that only exists on a Tailscale network:
 * - an IPv4 address of the 100.64.0.0/10 range Tailscale assigns to devices,
 * - an IPv6 address of the Tailscale `fd7a:115c:a1e0::/48` range,
 * - a MagicDNS name ending in `.ts.net`.
 *
 * Tailscale encrypts all of its traffic with WireGuard, so a plain HTTP URL to such an address
 * is not sent in clear text as long as the device is connected to the tailnet. This only tells
 * what the address looks like. Callers must also check that a VPN is currently active before
 * relying on it, since 100.64.0.0/10 is also used by some carrier-grade NAT networks.
 */
fun HttpUrl.isTailscaleAddress(): Boolean {
    val host = host.lowercase()
    return host.endsWith(TAILSCALE_MAGIC_DNS_SUFFIX) ||
        host.startsWith(TAILSCALE_IPV6_PREFIX) ||
        host.isTailscaleIpv4()
}

private fun String.isTailscaleIpv4(): Boolean {
    val parts = split('.')
    // Only plain digits: toIntOrNull would also accept signs such as "+100".
    val octets = parts
        .filter { part -> part.isNotEmpty() && part.all { it in '0'..'9' } }
        .mapNotNull { it.toIntOrNull() }
        .filter { it in IPV4_OCTET_RANGE }
    return parts.size == IPV4_OCTET_COUNT &&
        octets.size == IPV4_OCTET_COUNT &&
        octets[0] == TAILSCALE_IPV4_FIRST_OCTET &&
        octets[1] in TAILSCALE_IPV4_SECOND_OCTET
}
