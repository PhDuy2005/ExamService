package com.DoAn1.examservice.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.DoAn1.examservice.domain.response.RestResponse;
import com.DoAn1.examservice.util.annotation.ApiMessage;

import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class FormatRestResponse implements ResponseBodyAdvice<Object> {

    private static final String STORAGE_PATH_PREFIX = "/storage/";

    private final Path storageRootPath;

    public FormatRestResponse(@Value("${examservice.storage.root-path:D:/DoAn/DoAn1_storage}") String storageRootPath) {
        this.storageRootPath = Path.of(storageRootPath).toAbsolutePath().normalize();
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) {
            return body;
        }
        if (body instanceof String stringBody) {
            return toAbsoluteStoragePath(stringBody);
        }
        if (body instanceof RestResponse<?> restResponse) {
            normalizeStoragePaths(restResponse.getData(), newVisitedSet());
            return body;
        }
        if (body instanceof Resource || body instanceof byte[]
                || selectedContentType.includes(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))) {
            return body;
        }

        HttpServletResponse httpServletResponse = ((ServletServerHttpResponse) response).getServletResponse();
        int status = httpServletResponse.getStatus();
        if (status >= 400) {
            return body;
        }

        normalizeStoragePaths(body, newVisitedSet());
        ApiMessage apiMessage = returnType.getMethodAnnotation(ApiMessage.class);
        return RestResponse.builder()
                .statusCode(status)
                .message(apiMessage != null ? apiMessage.value() : "Call API success")
                .data(body)
                .build();
    }

    private Set<Object> newVisitedSet() {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private void normalizeStoragePaths(Object value, Set<Object> visited) {
        if (value == null || isSimpleValue(value)) {
            return;
        }
        if (!visited.add(value)) {
            return;
        }

        if (value instanceof List<?> list) {
            normalizeStoragePathsInList(list, visited);
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> normalizeStoragePaths(item, visited));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            normalizeStoragePathsInMap(map, visited);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> normalizeStoragePaths(item, visited));
            return;
        }
        if (!isApplicationType(value.getClass())) {
            return;
        }

        Class<?> currentClass = value.getClass();
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                normalizeStoragePathField(value, field, visited);
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private void normalizeStoragePathsInList(List<?> list, Set<Object> visited) {
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof String stringItem) {
                setListItem(list, i, toAbsoluteStoragePath(stringItem));
            } else {
                normalizeStoragePaths(item, visited);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void setListItem(List list, int index, String value) {
        try {
            list.set(index, value);
        } catch (UnsupportedOperationException ignored) {
            // Immutable lists with plain storage URL strings are left untouched.
        }
    }

    private void normalizeStoragePathsInMap(Map<?, ?> map, Set<Object> visited) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object entryValue = entry.getValue();
            if (entryValue instanceof String stringValue) {
                setMapValue(entry, toAbsoluteStoragePath(stringValue));
            } else {
                normalizeStoragePaths(entryValue, visited);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void setMapValue(Map.Entry entry, String value) {
        try {
            entry.setValue(value);
        } catch (UnsupportedOperationException ignored) {
            // Immutable maps with plain storage URL strings are left untouched.
        }
    }

    private void normalizeStoragePathField(Object target, Field field, Set<Object> visited) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers)) {
            return;
        }

        boolean accessible = field.canAccess(target);
        try {
            field.setAccessible(true);
            Object fieldValue = field.get(target);
            if (fieldValue instanceof String stringValue) {
                setRelativePathField(target, field, extractSafeStorageRelativePath(stringValue));
                field.set(target, toAbsoluteStoragePath(stringValue));
            } else {
                normalizeStoragePaths(fieldValue, visited);
            }
        } catch (IllegalAccessException ignored) {
            // If a field cannot be accessed, keep the original response value.
        } finally {
            field.setAccessible(accessible);
        }
    }

    private void setRelativePathField(Object target, Field sourceField, String relativePath) throws IllegalAccessException {
        if (relativePath == null) {
            return;
        }

        String relativeFieldName = toRelativePathFieldName(sourceField.getName());
        if (relativeFieldName == null) {
            return;
        }

        if (setRelativePathField(target, relativeFieldName, relativePath)) {
            return;
        }
        setRelativePathField(target, "relativePath", relativePath);
    }

    private boolean setRelativePathField(Object target, String relativeFieldName, String relativePath) throws IllegalAccessException {
        Field relativeField = findField(target.getClass(), relativeFieldName);
        if (relativeField == null || relativeField.getType() != String.class
                || Modifier.isStatic(relativeField.getModifiers())) {
            return false;
        }

        boolean accessible = relativeField.canAccess(target);
        try {
            relativeField.setAccessible(true);
            relativeField.set(target, relativePath);
            return true;
        } finally {
            relativeField.setAccessible(accessible);
        }
    }

    private String toRelativePathFieldName(String fieldName) {
        if (fieldName == null || fieldName.endsWith("RelativePath")) {
            return null;
        }
        if (fieldName.endsWith("Url")) {
            return fieldName.substring(0, fieldName.length() - "Url".length()) + "RelativePath";
        }
        if (fieldName.endsWith("Path")) {
            return fieldName.substring(0, fieldName.length() - "Path".length()) + "RelativePath";
        }
        return null;
    }

    private Field findField(Class<?> valueClass, String fieldName) {
        Class<?> currentClass = valueClass;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    private boolean isSimpleValue(Object value) {
        Class<?> valueClass = value.getClass();
        return valueClass.isPrimitive()
                || valueClass.isEnum()
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof java.time.temporal.TemporalAccessor
                || value instanceof java.util.UUID;
    }

    private boolean isApplicationType(Class<?> valueClass) {
        Package valuePackage = valueClass.getPackage();
        return valuePackage != null && valuePackage.getName().startsWith("com.DoAn1.examservice");
    }

    private String toAbsoluteStoragePath(String value) {
        String relativePath = extractSafeStorageRelativePath(value);
        if (relativePath == null) {
            return value;
        }

        Path resolvedPath = storageRootPath.resolve(relativePath).normalize();
        return resolvedPath.toString();
    }

    private String extractSafeStorageRelativePath(String value) {
        String relativePath = extractStorageRelativePath(value);
        if (relativePath == null) {
            return null;
        }

        Path resolvedPath = storageRootPath.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(storageRootPath)) {
            return null;
        }
        return relativePath;
    }

    private String extractStorageRelativePath(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith(STORAGE_PATH_PREFIX)) {
            return value.substring(STORAGE_PATH_PREFIX.length());
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                String path = new URI(value).getPath();
                if (path != null && path.startsWith(STORAGE_PATH_PREFIX)) {
                    return path.substring(STORAGE_PATH_PREFIX.length());
                }
            } catch (URISyntaxException ignored) {
                return null;
            }
        }
        return null;
    }
}

