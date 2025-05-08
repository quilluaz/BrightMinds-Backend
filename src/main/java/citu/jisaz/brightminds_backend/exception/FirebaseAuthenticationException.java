package citu.jisaz.brightminds_backend.exception;

import lombok.Getter;

@Getter
public class FirebaseAuthenticationException extends RuntimeException {

    private final boolean userNotFoundInDb;

    public FirebaseAuthenticationException(String message) {
        super(message);
        this.userNotFoundInDb = false;
    }

    public FirebaseAuthenticationException(String message, Throwable cause) {
        super(message, cause);
        this.userNotFoundInDb = false;
    }

    public FirebaseAuthenticationException(String message, boolean userNotFoundInDb) {
        super(message);
        this.userNotFoundInDb = userNotFoundInDb;
    }

    public FirebaseAuthenticationException(String message, Throwable cause, boolean userNotFoundInDb) {
        super(message, cause);
        this.userNotFoundInDb = userNotFoundInDb;
    }

}