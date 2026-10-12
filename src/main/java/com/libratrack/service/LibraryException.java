package com.libratrack.service;

/** Raised when a library operation is rejected by a business rule. */
public class LibraryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LibraryException(String message) {
        super(message);
    }
}
