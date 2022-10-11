package com.alibaba.compileflow.engine.common.extension.exception;

/**
 * @author yusu
 */
public class PluginException extends RuntimeException {

    /**
     * the error code.
     */
    private String errorCode;

    /**
     * the error message.
     */
    private String errorMessage;

    public PluginException(String message, Throwable cause) {
        super(cause);
        this.errorMessage = message + "," + cause.getMessage();
    }

    public PluginException(String errorMessage) {
        super(errorMessage);
        this.errorMessage = errorMessage;
    }

    public PluginException(Exception e) {
        super(e);
        this.errorMessage = e.getMessage();
    }

    public PluginException(String errorCode, String errorMessage) {
        this.errorMessage = errorMessage;
        this.errorCode = errorCode;
    }

    public PluginException(String code, String errorMessage, Exception e) {
        super(e);
        this.errorMessage = errorMessage + "," + e.getMessage();
        this.errorCode = code;
    }

}
