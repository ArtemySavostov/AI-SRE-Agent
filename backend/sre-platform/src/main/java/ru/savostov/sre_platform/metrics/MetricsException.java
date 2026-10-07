package ru.savostov.sre_platform.metrics;

public class MetricsException extends RuntimeException {
    private final String code;
    public MetricsException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() { return code; }
}
