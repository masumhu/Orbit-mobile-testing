package com.devhub.auth;

public class AuthException extends RuntimeException {
    public AuthException() { super("Invalid or expired authentication credentials"); }
}
