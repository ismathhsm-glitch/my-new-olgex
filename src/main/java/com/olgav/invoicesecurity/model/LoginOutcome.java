package com.olgav.invoicesecurity.model;

public enum LoginOutcome {
    UNKNOWN,
    SUCCESS,
    INVALID_CREDENTIALS,
    FACE_VERIFICATION_REQUIRED,
    FACE_VERIFICATION_FAILED,
    FACE_VERIFIED,
    HIGH_RISK_LOGIN
}
