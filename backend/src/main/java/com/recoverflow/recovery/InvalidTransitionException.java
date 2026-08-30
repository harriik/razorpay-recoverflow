package com.recoverflow.recovery;

public class InvalidTransitionException extends RuntimeException {
    private final RecoveryCaseStatus from;
    private final RecoveryCaseStatus to;

    public InvalidTransitionException(RecoveryCaseStatus from, RecoveryCaseStatus to) {
        super("Invalid transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public RecoveryCaseStatus getFrom() { return from; }
    public RecoveryCaseStatus getTo() { return to; }
}
