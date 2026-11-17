package org.example.citywatch.use_case.gateway;


/** Storage/catalog failure that use cases can handle without depending on file APIs. */
public class DataAccessException extends Exception {
    public DataAccessException(String message) { super(message); }
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
