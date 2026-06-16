package com.DoAn1.examservice.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.util.StringUtils;

public class StorageUrlPrefixResolver {

    public static final String DEFAULT_STORAGE_PATH_PREFIX = "/storage/";

    private final List<UrlPrefix> urlPrefixes;
    private final List<String> pathPrefixes;

    public StorageUrlPrefixResolver(String configuredPrefixes) {
        Set<UrlPrefix> normalizedUrlPrefixes = new LinkedHashSet<>();
        Set<String> normalizedPathPrefixes = new LinkedHashSet<>();
        normalizedPathPrefixes.add(DEFAULT_STORAGE_PATH_PREFIX);

        for (String prefix : splitPrefixes(configuredPrefixes)) {
            addPrefix(prefix, normalizedUrlPrefixes, normalizedPathPrefixes);
        }

        this.urlPrefixes = List.copyOf(normalizedUrlPrefixes);
        this.pathPrefixes = List.copyOf(normalizedPathPrefixes);
    }

    public String extractRelativePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmedValue = value.trim();
        if (isHttpUrl(trimmedValue)) {
            try {
                URI uri = new URI(trimmedValue);
                String relativePath = extractFromConfiguredUrlPrefixes(uri);
                if (relativePath != null) {
                    return relativePath;
                }
                return extractFromPathPrefixes(uri.getPath());
            } catch (URISyntaxException ex) {
                return null;
            }
        }

        return extractFromPathPrefixes(trimmedValue);
    }

    private List<String> splitPrefixes(String configuredPrefixes) {
        if (!StringUtils.hasText(configuredPrefixes)) {
            return List.of();
        }

        List<String> prefixes = new ArrayList<>();
        for (String prefix : configuredPrefixes.split(",")) {
            if (StringUtils.hasText(prefix)) {
                prefixes.add(prefix.trim());
            }
        }
        return prefixes;
    }

    private void addPrefix(
            String prefix,
            Set<UrlPrefix> normalizedUrlPrefixes,
            Set<String> normalizedPathPrefixes) {
        if (isHttpUrl(prefix)) {
            try {
                URI uri = new URI(prefix);
                String host = uri.getHost();
                if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(host)) {
                    return;
                }
                normalizedUrlPrefixes.add(new UrlPrefix(
                        uri.getScheme().toLowerCase(Locale.ROOT),
                        host.toLowerCase(Locale.ROOT),
                        uri.getPort(),
                        normalizePathPrefix(uri.getPath())));
            } catch (URISyntaxException ignored) {
                return;
            }
            return;
        }

        normalizedPathPrefixes.add(normalizePathPrefix(prefix));
    }

    private String extractFromConfiguredUrlPrefixes(URI uri) {
        for (UrlPrefix prefix : urlPrefixes) {
            if (prefix.matches(uri)) {
                return stripPathPrefix(uri.getPath(), prefix.pathPrefix());
            }
        }
        return null;
    }

    private String extractFromPathPrefixes(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }

        for (String pathPrefix : pathPrefixes) {
            if (path.startsWith(pathPrefix)) {
                return stripPathPrefix(path, pathPrefix);
            }
        }
        return null;
    }

    private String stripPathPrefix(String path, String prefix) {
        String relativePath = path.substring(prefix.length()).replace("\\", "/");
        return relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
    }

    private String normalizePathPrefix(String prefix) {
        String normalizedPrefix = StringUtils.hasText(prefix) ? prefix.trim().replace("\\", "/") : "/";
        if (!normalizedPrefix.startsWith("/")) {
            normalizedPrefix = "/" + normalizedPrefix;
        }
        if (!normalizedPrefix.endsWith("/")) {
            normalizedPrefix = normalizedPrefix + "/";
        }
        return normalizedPrefix;
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private record UrlPrefix(String scheme, String host, int port, String pathPrefix) {

        private boolean matches(URI uri) {
            String uriScheme = uri.getScheme();
            String uriHost = uri.getHost();
            return uriScheme != null
                    && uriHost != null
                    && scheme.equalsIgnoreCase(uriScheme)
                    && host.equalsIgnoreCase(uriHost)
                    && port == uri.getPort()
                    && uri.getPath() != null
                    && uri.getPath().startsWith(pathPrefix);
        }
    }
}
