package fr.xephi.authme.settings.properties;

import ch.jalu.configme.Comment;
import ch.jalu.configme.SettingsHolder;
import ch.jalu.configme.properties.Property;
import fr.xephi.authme.service.EmailVerificationSendFailureAction;

import java.util.List;

import static ch.jalu.configme.properties.PropertyInitializer.newListProperty;
import static ch.jalu.configme.properties.PropertyInitializer.newProperty;

public final class EmailSettings implements SettingsHolder {

    @Comment("Email SMTP server host")
    public static final Property<String> SMTP_HOST =
        newProperty("Email.mailSMTP", "smtp.gmail.com");

    @Comment({"Email SMTP server port. The port determines the encryption mode:",
              "  25  -> plain SMTP; optional STARTTLS via 'useTls' (see below)",
              "  465 -> implicit SSL/TLS (SMTPS); 'useTls' is ignored",
              "  587 -> STARTTLS required (submission); 'useTls' is ignored",
              "  other -> STARTTLS required; 'useTls' is ignored"})
    public static final Property<Integer> SMTP_PORT =
        newProperty("Email.mailPort", 465);

    @Comment({"Only applies to port 25: enable STARTTLS on the plain SMTP connection?",
              "Has no effect when using port 465 (SSL) or 587 (STARTTLS), which enforce",
              "their own encryption and cannot be overridden by this setting."})
    public static final Property<Boolean> PORT25_USE_TLS =
        newProperty("Email.useTls", true);

    @Comment("Email account which sends the mails")
    public static final Property<String> MAIL_ACCOUNT =
        newProperty("Email.mailAccount", "");

    @Comment("Email account password")
    public static final Property<String> MAIL_PASSWORD =
        newProperty("Email.mailPassword", "");

    @Comment("Email address, fill when mailAccount is not the email address of the account")
    public static final Property<String> MAIL_ADDRESS =
        newProperty("Email.mailAddress", "");

    @Comment("Custom sender name, replacing the mailAccount name in the email")
    public static final Property<String> MAIL_SENDER_NAME =
        newProperty("Email.mailSenderName", "");

    @Comment("Recovery password length")
    public static final Property<Integer> RECOVERY_PASSWORD_LENGTH =
        newProperty("Email.RecoveryPasswordLength", 8);

    @Comment("Mail Subject")
    public static final Property<String> RECOVERY_MAIL_SUBJECT =
        newProperty("Email.mailSubject", "Your new AuthMe password");

    @Comment("Like maxRegPerIP but with email")
    public static final Property<Integer> MAX_REG_PER_EMAIL =
        newProperty("Email.maxRegPerEmail", 1);

    @Comment("Recall players to add an email?")
    public static final Property<Boolean> RECALL_PLAYERS =
        newProperty("Email.recallPlayers", false);

    @Comment("Delay in minute for the recall scheduler")
    public static final Property<Integer> DELAY_RECALL =
        newProperty("Email.delayRecall", 5);

    @Comment("Blacklist these domains for emails")
    public static final Property<List<String>> DOMAIN_BLACKLIST =
        newListProperty("Email.emailBlacklisted", "10minutemail.com");

    @Comment("Whitelist ONLY these domains for emails")
    public static final Property<List<String>> DOMAIN_WHITELIST =
        newListProperty("Email.emailWhitelisted");

    @Comment("Send the new password drawn in an image?")
    public static final Property<Boolean> PASSWORD_AS_IMAGE =
        newProperty("Email.generateImage", false);

    @Comment("The OAuth2 token")
    public static final Property<String> OAUTH2_TOKEN =
        newProperty("Email.emailOauth2Token", "");

    @Comment({"Verify the SSL/TLS server certificate hostname?",
              "Only applies when an SSL/TLS connection is active (port 465, port 587,",
              "port 25 with useTls=true, or any other port).",
              "Set to false only if your SMTP server uses a self-signed certificate.",
              "Note: if you previously used port 465, this check was not enforced;",
              "set to false to restore the old behavior with a self-signed certificate."})
    public static final Property<Boolean> SSL_CHECK_SERVER_IDENTITY =
        newProperty("Email.sslCheckServerIdentity", true);

    @Comment({"Require players to verify their email address with a code sent by mail",
              "before they can play? Requires the SMTP settings above to be complete."})
    public static final Property<Boolean> VERIFICATION_ENABLED =
        newProperty("Email.verification.enabled", false);

    @Comment("Length of the numeric email verification code")
    public static final Property<Integer> VERIFICATION_CODE_LENGTH =
        newProperty("Email.verification.codeLength", 6);

    @Comment("Minutes an email verification code remains valid")
    public static final Property<Integer> VERIFICATION_CODE_VALIDITY_MINUTES =
        newProperty("Email.verification.codeValidityMinutes", 10);

    @Comment("Cooldown in milliseconds before the same player can request another verification code")
    public static final Property<Long> VERIFICATION_PERSONAL_COOLDOWN_MS =
        newProperty("Email.verification.personalResendCooldownMs", 60_000L);

    @Comment({"Global cooldown in milliseconds between any two verification mails.",
              "While active, other players' send requests are rejected."})
    public static final Property<Long> VERIFICATION_GLOBAL_COOLDOWN_MS =
        newProperty("Email.verification.globalSendCooldownMs", 1_000L);

    @Comment("Maximum wrong code attempts before the player is kicked. 0 = unlimited")
    public static final Property<Integer> VERIFICATION_MAX_ATTEMPTS =
        newProperty("Email.verification.maxAttempts", 5);

    @Comment("Seconds a player may stay in the email verification gate before being kicked")
    public static final Property<Integer> VERIFICATION_TIMEOUT_SECONDS =
        newProperty("Email.verification.timeoutSeconds", 300);

    @Comment({"Action when the verification mail could not be sent:",
              "RETRY = keep the player in the gate so he can retry,",
              "KICK = kick the player,",
              "ALLOW = let the player play this time (email stays unverified)"})
    public static final Property<EmailVerificationSendFailureAction> VERIFICATION_SEND_FAILURE_ACTION =
        newProperty(EmailVerificationSendFailureAction.class, "Email.verification.sendFailureAction",
            EmailVerificationSendFailureAction.RETRY);

    private EmailSettings() {
    }

}
