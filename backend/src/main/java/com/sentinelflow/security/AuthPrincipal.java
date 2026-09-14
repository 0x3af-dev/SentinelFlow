package com.sentinelflow.security;

/** Holder for the authenticated principal, supplied to controllers via
 *  {@code @AuthenticationPrincipal}. Never trust client-supplied
 *  identity fields; always derive actor reference from this record. */
public record AuthPrincipal(String username, String role) {
}
