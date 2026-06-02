package com.DoAn1.examservice.util;

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
        if (body instanceof RestResponse<?> || body instanceof String || body instanceof Resource || body instanceof byte[]
                || selectedContentType.includes(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))) {
            return body;
        }

        HttpServletResponse httpServletResponse = ((ServletServerHttpResponse) response).getServletResponse();
        int status = httpServletResponse.getStatus();
        if (status >= 400) {
            return body;
        }

        ApiMessage apiMessage = returnType.getMethodAnnotation(ApiMessage.class);
        return RestResponse.builder()
                .statusCode(status)
                .message(apiMessage != null ? apiMessage.value() : "Call API success")
                .data(body)
                .build();
    }
}

