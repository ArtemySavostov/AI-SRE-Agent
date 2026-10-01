package ru.savostov.sre_platform.discovery;

public class DiscoveryException extends RuntimeException {
    private final String code;
    public DiscoveryException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() { return code; }
}
