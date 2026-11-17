package org.example.citywatch.use_case.gateway;

public class ProfileStorageException extends RuntimeException {
    public ProfileStorageException(String message) {
        super(message);
    }

    public ProfileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}