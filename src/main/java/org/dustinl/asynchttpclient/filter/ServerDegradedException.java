package org.dustinl.asynchttpclient.filter;

public class ServerDegradedException extends RuntimeException {
    public ServerDegradedException() {
    }

    public ServerDegradedException(String message) {
        super(message);
    }

    public ServerDegradedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServerDegradedException(Throwable cause) {
        super(cause);
    }

    public ServerDegradedException(String message, Throwable cause,
                                   boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
