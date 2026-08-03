package fr.xephi.authme.service;

/**
 * Action to take when the email verification code could not be sent.
 */
public enum EmailVerificationSendFailureAction {

    /** Keep the player in the verification gate so he can retry. */
    RETRY,

    /** Kick the player. */
    KICK,

    /** Let the player play this time; the email stays unverified. */
    ALLOW
}
