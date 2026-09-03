package com.recoverflow.ai;

public class LlmTimeoutException extends Exception {
    public LlmTimeoutException(String msg) { super(msg); }
    public LlmTimeoutException(String msg, Throwable cause) { super(msg, cause); }
}
