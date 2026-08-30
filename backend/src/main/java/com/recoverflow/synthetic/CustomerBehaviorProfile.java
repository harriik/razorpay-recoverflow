package com.recoverflow.synthetic;

public enum CustomerBehaviorProfile {
    STRONG_HISTORY,    // 10+ prior successes
    NEW_CUSTOMER,      // 0 history
    REPEAT_FAILURE,    // 3+ failures
    HIGH_FRICTION,     // high synthetic friction proxy
    AVERAGE
}
