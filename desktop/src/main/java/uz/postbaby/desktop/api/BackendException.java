package uz.postbaby.desktop.api;

public class BackendException extends RuntimeException {
    public final int status;
    public final String body;

    public BackendException(int status, String body) {
        super("Backend " + status + ": " + body);
        this.status = status;
        this.body = body;
    }

    public BackendException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
        this.body = "";
    }

    public boolean isNetwork() {
        return status == -1;
    }

    public boolean isUnauthorized() {
        return status == 401;
    }
}
