package com.recoverflow.recovery;

public enum RecoveryActionType {
    RETRY_NOW,
    SCHEDULE_RETRY,
    SEND_PAYMENT_LINK,
    SEND_REMINDER,
    ESCALATE,
    STOP
}
