package fr.xephi.authme.process.email;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.AsynchronousProcess;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.EmailVerificationGate;
import fr.xephi.authme.service.EmailVerificationService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.util.Utils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Asynchronous process behind {@code /email verify}: submits codes, resends codes
 * and changes the email address of players inside the verification gate.
 */
public class AsyncEmailVerification implements AsynchronousProcess {

    @Inject
    private CommonService service;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private EmailVerificationService verificationService;

    @Inject
    private EmailVerificationGate gate;

    @Inject
    private ValidationService validationService;

    AsyncEmailVerification() {
    }

    /**
     * Returns whether the player may use {@code /email verify} right now:
     * the feature is active and the player is either held in the gate or has
     * an unverified email address.
     * Only performs reads, so it is safe to call from a synchronous context.
     *
     * @param player the player to check
     * @return true if the command is applicable to the player
     */
    public boolean mayUse(Player player) {
        if (!verificationService.isActive()) {
            return false;
        }
        String name = player.getName().toLowerCase(Locale.ROOT);
        if (gate.isGated(name)) {
            return true;
        }
        PlayerAuth auth = playerCache.getAuth(name);
        return auth != null && !auth.isEmailVerified() && !Utils.isEmailEmpty(auth.getEmail());
    }

    /**
     * Verifies the code submitted by the player.
     *
     * @param player the player submitting the code
     * @param code the submitted code
     */
    public void submitCode(Player player, String code) {
        String name = player.getName().toLowerCase(Locale.ROOT);
        switch (verificationService.verifyCode(player.getName(), code)) {
            case SUCCESS:
                service.send(player, MessageKey.EMAIL_VERIFICATION_SUCCESS);
                if (gate.isGated(name)) {
                    gate.completeGate(player);
                }
                break;
            case WRONG_CODE:
                service.send(player, MessageKey.EMAIL_VERIFICATION_WRONG_CODE,
                    String.valueOf(verificationService.getAttemptsRemaining(name)));
                break;
            case MAX_ATTEMPTS:
                gate.kickFromGate(player, MessageKey.EMAIL_VERIFICATION_MAX_ATTEMPTS_KICK);
                break;
            case EXPIRED_OR_NONE:
            default:
                service.send(player, MessageKey.USAGE_EMAIL_VERIFY);
        }
    }

    /**
     * Resends the verification code to the player's email address.
     *
     * @param player the player requesting a new code
     */
    public void resend(Player player) {
        String name = player.getName().toLowerCase(Locale.ROOT);
        PlayerAuth auth = playerCache.getAuth(name);
        if (auth == null || Utils.isEmailEmpty(auth.getEmail())) {
            service.send(player, MessageKey.EMAIL_VERIFICATION_BINDING_REQUIRED);
            return;
        }
        handleSendResult(player, verificationService.sendCode(player.getName(), auth.getEmail()),
            auth.getEmail());
    }

    /**
     * Changes the player's email address and sends a verification code to the new address.
     *
     * @param player the player changing his address
     * @param email the new email address
     */
    public void setEmail(Player player, String email) {
        if (!validationService.validateEmail(email)) {
            service.send(player, MessageKey.INVALID_EMAIL);
            return;
        }
        EmailVerificationService.SendCodeResult result =
            verificationService.changeEmailAndResend(player.getName(), email);
        if (result == EmailVerificationService.SendCodeResult.SENT) {
            service.send(player, MessageKey.EMAIL_VERIFICATION_EMAIL_CHANGED, email);
        }
        handleSendResult(player, result, email);
    }

    private void handleSendResult(Player player, EmailVerificationService.SendCodeResult result, String email) {
        String name = player.getName().toLowerCase(Locale.ROOT);
        switch (result) {
            case SENT:
                service.send(player, MessageKey.EMAIL_VERIFICATION_SENT, email,
                    String.valueOf(service.getProperty(EmailSettings.VERIFICATION_CODE_VALIDITY_MINUTES)));
                break;
            case PERSONAL_COOLDOWN:
                service.send(player, MessageKey.EMAIL_VERIFICATION_RESEND_COOLDOWN,
                    String.valueOf(verificationService.getPersonalCooldownRemainingSeconds(name)));
                break;
            case GLOBAL_COOLDOWN:
                service.send(player, MessageKey.EMAIL_VERIFICATION_GLOBAL_COOLDOWN);
                break;
            case SEND_FAILED:
            default:
                handleSendFailure(player, name);
        }
    }

    private void handleSendFailure(Player player, String name) {
        switch (service.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION)) {
            case KICK:
                gate.kickFromGate(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
                break;
            case ALLOW:
                if (gate.isGated(name)) {
                    gate.completeGate(player);
                } else {
                    service.send(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
                }
                break;
            case RETRY:
            default:
                service.send(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
        }
    }
}
