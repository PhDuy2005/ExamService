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
        return getCurrentUserClaimValue("id");
    }

    public static Optional<String> getCurrentUserFullName() {
        return getCurrentUserClaimValue("fullName");
    }

    public static Optional<String> getCurrentStudentId() {
        return getCurrentUserClaimValue("studentId");
    }

    public static Optional<String> getCurrentRoleName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Map<String, Object> roleClaim = jwt.getClaimAsMap("role");
            if (roleClaim == null) {
                return Optional.empty();
            }

            Object roleName = roleClaim.get("roleName");
            if (roleName == null) {
                return Optional.empty();
            }

            return Optional.of(roleName.toString());
        }
        return Optional.empty();
    }

    public static boolean isCurrentUserStudent() {
        return getCurrentRoleName()
                .map(roleName -> "STUDENT".equalsIgnoreCase(roleName))
                .orElse(false);
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

    private static Optional<String> getCurrentUserClaimValue(String fieldName) {
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

            Object value = userClaim.get(fieldName);
            if (value == null) {
                return Optional.empty();
            }

            return Optional.of(value.toString());
        }
        return Optional.empty();
    }
}

