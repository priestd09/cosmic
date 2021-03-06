package com.cloud.legacymodel.exceptions;

/**
 * Used by the NioConnection class to wrap-up its exceptions.
 */
public class NioConnectionException extends Exception {
    protected int csErrorCode;

    public NioConnectionException(final String msg, final Throwable cause) {
        super(msg, cause);
        setCSErrorCode(CSExceptionErrorCode.getCSErrCode(this.getClass().getName()));
    }

    public NioConnectionException(final String msg) {
        super(msg);
    }

    public int getCSErrorCode() {
        return csErrorCode;
    }

    public void setCSErrorCode(final int cserrcode) {
        csErrorCode = cserrcode;
    }
}
