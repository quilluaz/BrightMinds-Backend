package citu.jisaz.brightminds_backend.advice;

import citu.jisaz.brightminds_backend.exception.BadRequestException;
import citu.jisaz.brightminds_backend.exception.EmailAlreadyExistsException;
import citu.jisaz.brightminds_backend.exception.FirebaseAuthenticationException;
import citu.jisaz.brightminds_backend.exception.ResourceNotFoundException;
import citu.jisaz.brightminds_backend.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.lang.NonNull;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Object> buildErrorResponse(
            HttpStatus status, String message, WebRequest request, Exception ex, Map<String, Object> additionalFields) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", ((ServletWebRequest) request).getRequest().getRequestURI());

        if (additionalFields != null && !additionalFields.isEmpty()) {
            body.putAll(additionalFields);
        }

        if (status.is5xxServerError()) {
            logger.error("Unhandled exception for request {}: {}", ((ServletWebRequest) request).getRequest().getRequestURI(), message, ex);
        } else {
            logger.warn("Handled exception for request {}: {} (Type: {})",
                    ((ServletWebRequest) request).getRequest().getRequestURI(), message, ex.getClass().getSimpleName());
        }


        return new ResponseEntity<>(body, status);
    }

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message, WebRequest request, Exception ex) {
        return buildErrorResponse(status, message, request, ex, null);
    }


    @ExceptionHandler({ResourceNotFoundException.class, UserNotFoundException.class})
    public ResponseEntity<Object> handleResourceNotFoundException(
            RuntimeException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Object> handleEmailAlreadyExistsException(
            EmailAlreadyExistsException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequestException(
            BadRequestException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(FirebaseAuthenticationException.class)
    public ResponseEntity<Object> handleFirebaseAuthenticationException(
            FirebaseAuthenticationException ex, WebRequest request) {
        HttpStatus status = ex.isUserNotFoundInDb() ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
        String clientMessage = status == HttpStatus.FORBIDDEN ?
                "Access denied. User account not configured properly or does not exist in application records." :
                "Authentication failed. Invalid or expired credentials.";
        return buildErrorResponse(status, clientMessage, request, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request, ex);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value",
                        (existingValue, newValue) -> existingValue + "; " + newValue)
                );

        Map<String, Object> additionalFields = new HashMap<>();
        additionalFields.put("errors", fieldErrors);

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation failed. Check 'errors' field for details.", request, ex, additionalFields);
    }

    @ExceptionHandler(com.google.firebase.auth.FirebaseAuthException.class)
    public ResponseEntity<Object> handleFirebaseAuthSdkException(
            com.google.firebase.auth.FirebaseAuthException ex, WebRequest request) {
        logger.error("Firebase SDK Exception: Code='{}', Message='{}' for request path: {}",
                ex.getAuthErrorCode(), ex.getMessage(), ((ServletWebRequest)request).getRequest().getRequestURI(), ex);

        String clientMessage = "An authentication or user management error occurred with the identity provider.";
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (ex.getAuthErrorCode() != null) {
            switch (ex.getAuthErrorCode()) {
                case USER_NOT_FOUND:
                    status = HttpStatus.NOT_FOUND;
                    clientMessage = "User account not found with the identity provider.";
                    break;
                case EMAIL_ALREADY_EXISTS:
                    status = HttpStatus.CONFLICT;
                    clientMessage = "Email is already in use by another account.";
                    break;
                case INVALID_ID_TOKEN:
                case EXPIRED_ID_TOKEN:
                case REVOKED_ID_TOKEN:
                    status = HttpStatus.UNAUTHORIZED;
                    clientMessage = "Authentication token is invalid, expired, or revoked.";
                    break;
                default:
                    break;
            }
        }
        return buildErrorResponse(status, clientMessage, request, ex);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(
            Exception ex, WebRequest request) {
        String message = "An unexpected error occurred. Please try again later.";
        logger.error("Unhandled generic exception for request {}: {}", ((ServletWebRequest) request).getRequest().getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, request, ex);
    }
}