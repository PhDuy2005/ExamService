package com.DoAn1.examservice.util;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityUtil {

    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS256;

    private SecurityUtil() {
    }

    public static Optional<String> getCurrentUserLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }

    public static Optional<String> getCurrentUserUuid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Map<String, Object> userClaim = jwt.getClaimAsMap("user");
            if (userClaim == null) {
                return Optional.empty();
            }

            Object userId = userClaim.get("id");
            if (userId == null) {
                return Optional.empty();
            }

            return Optional.of(userId.toString());
        }
        return Optional.empty();
    }

    public static boolean hasCurrentUserAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}

