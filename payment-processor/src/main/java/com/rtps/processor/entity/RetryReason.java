package com.rtps.processor.entity;

public enum RetryReason {
    BANK_UNAVAILABLE,
    ACCOUNT_LOCKED,
    INSUFFICIENT_BALANCE,
    INVALID_ACCOUNT,
    ACCOUNT_FROZEN,
    GENERIC_ERROR
}
