package com.hostlify.shared.security;

import java.lang.annotation.*;

/**
 * Method parameter annotation to inject the currently authenticated UserPrincipal.
 *
 * Usage:
 *   public ResponseEntity<?> getProfile(@CurrentUser UserPrincipal user) { ... }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
