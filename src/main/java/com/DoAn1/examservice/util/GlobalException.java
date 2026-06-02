package com.DoAn1.examservice.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.DoAn1.examservice.domain.response.RestResponse;
import com.DoAn1.examservice.exception.IdInvalidException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalException {

    // handle all other exceptions
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<RestResponse<Object>> handleAllExceptions(Exception ex) {
        log.error("Unhandled exception captured: type={}, message={}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        restResponse.setError("An unexpected Internal server error occurred");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(restResponse);
    }

    @ExceptionHandler(value = {
            UsernameNotFoundException.class,
            BadCredentialsException.class
    })
    public ResponseEntity<Object> handleSecurityException(Exception ex) {
        RestResponse<Object> res = new RestResponse<Object>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError("Exception about login function occurs...");
        res.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(res);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Object>> handleValidationError(
            MethodArgumentNotValidException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();

        BindingResult bindingResult = ex.getBindingResult();
        final List<FieldError> fieldErrors = bindingResult.getFieldErrors();

        restResponse.setStatusCode(HttpStatus.BAD_REQUEST.value());
        restResponse.setError(ex.getBody().getDetail());

        List<String> errorMessages = fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        restResponse.setMessage(errorMessages.size() > 1 ? errorMessages : errorMessages.get(0));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(restResponse);
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    public ResponseEntity<RestResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.BAD_REQUEST.value());
        restResponse.setError("Illegal argument exception occurs...");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(restResponse);
    }

    @ExceptionHandler(value = NoSuchElementException.class)
    public ResponseEntity<RestResponse<Object>> handleNoSuchElementException(NoSuchElementException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.NOT_FOUND.value());
        restResponse.setError("No such element found...");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(restResponse);
    }

    @ExceptionHandler(value = NoResourceFoundException.class)
    public ResponseEntity<RestResponse<Object>> handleNoResourceFoundException(NoResourceFoundException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.NOT_FOUND.value());
        restResponse.setError("404 Error. No resource found... Please check your URL");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(restResponse);
    }

    @ExceptionHandler(value = BadRequestException.class)
    public ResponseEntity<RestResponse<Object>> handleBadRequestException(BadRequestException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.BAD_REQUEST.value());
        if (ex.getMessage().equals("No refresh token provided")) {
            restResponse.setError("401 Unauthorized Exception occurs: Invalid refresh token");
            restResponse.setMessage(ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(restResponse);
        }
        if (ex.getMessage().equals("Invalid refresh token")) {
            restResponse.setError("401 Unauthorized Exception occurs: Invalid refresh token");
            restResponse.setMessage(ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(restResponse);
        }
        restResponse.setError("400 Bad Request Exception occurs... Please check your request's header or payload");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(restResponse);
    }

    // @ExceptionHandler(value = StorageException.class)
    // public ResponseEntity<RestResponse<Object>>
    // handleFileUploadException(StorageException ex) {
    // RestResponse<Object> restResponse = new RestResponse<>();
    // restResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    // restResponse.setError("File upload exception occurs...");
    // restResponse.setMessage(ex.getMessage());
    // return
    // ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(restResponse);
    // }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<RestResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.FORBIDDEN.value());
        restResponse.setError("403 Forbidden Exception occurs: Access is denied");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(restResponse);
    }

    @ExceptionHandler(value = InsufficientAuthenticationException.class)
    public ResponseEntity<RestResponse<Object>> handleInsufficientAuthenticationException(
            InsufficientAuthenticationException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.UNAUTHORIZED.value());
        restResponse.setError("401 Unauthorized Exception occurs: You must login first");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(restResponse);
    }

    @ExceptionHandler(value = IdInvalidException.class)
    public ResponseEntity<RestResponse<Object>> handleIdInvalidException(IdInvalidException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.NOT_FOUND.value());
        restResponse.setError("Resource not found");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(restResponse);
    }

    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public ResponseEntity<RestResponse<Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.BAD_REQUEST.value());
        restResponse.setError("Invalid request body");

        // Extract more meaningful error message
        String message = ex.getMessage();
        if (message != null && message.contains("Cannot deserialize value of type")) {
            // Extract the specific error for enum or type mismatch
            if (message.contains("DayOfWeekEnum")) {
                restResponse.setMessage(
                        "Invalid day of week. Allowed values: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY");
            } else {
                restResponse.setMessage("Invalid data format in request body");
            }
        } else {
            restResponse.setMessage("Malformed JSON request");
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(restResponse);
    }

    @ExceptionHandler(value = URISyntaxException.class)
    public ResponseEntity<RestResponse<Object>> handleURISyntaxException(URISyntaxException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.BAD_REQUEST.value());
        restResponse.setError("Invalid URI syntax");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(restResponse);
    }

    @ExceptionHandler(value = IOException.class)
    public ResponseEntity<RestResponse<Object>> handleIOException(IOException ex) {
        RestResponse<Object> restResponse = new RestResponse<>();
        restResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        restResponse.setError("I/O Exception occurred");
        restResponse.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(restResponse);
    }
}
