package fr.xephi.authme.service;

import com.google.common.annotations.VisibleForTesting;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.initialization.HasCleanup;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.util.RandomStringUtils;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages email ownership verification codes: generation, send cooldowns,
 * validation and persistence of the verified flag.
 * All public methods must be called from an asynchronous context (database / mail access).
 */
public class EmailVerificationService implements SettingsDependent, HasCleanup {

    private final DataSource dataSource;
    private final EmailService emailService;
    private final PlayerCache playerCache;

    private final Map<String, PendingVerification> pendingCodes = new ConcurrentHashMap<>();
    private final AtomicLong lastGlobalSendAt = new AtomicLong();

    private boolean active;
    private int codeLength;
    private long codeValidityMs;
    private int codeValidityMinutes;
    private long personalCooldownMs;
    private long globalCooldownMs;
    private int maxAttempts;

    @Inject
    EmailVerificationService(Settings settings, DataSource dataSource, EmailService emailService,
                             PlayerCache playerCache) {
        this.dataSource = dataSource;
        this.emailService = emailService;
        this.playerCache = playerCache;
        reload(settings);
    }

    /**
     * Returns whether the email verification feature is active (enabled and mail settings complete).
     *
     * @return true if active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns whether the given auth must pass email verification before playing.
     * Note: also true when no email is set (the gate then forces binding first).
     *
     * @param auth the auth to check
     * @return true if verification is required
     */
    public boolean isVerificationRequired(PlayerAuth auth) {
        return active && auth != null && !auth.isEmailVerified();
    }

    /**
     * Returns whether the player has a pending, not yet expired verification code.
     *
     * @param name the player name
     * @return true if a valid pending code exists
     */
    public boolean hasPendingCode(String name) {
        PendingVerification pv = pendingCodes.get(name.toLowerCase(Locale.ROOT));
        if (pv == null) {
            return false;
        }
        if (System.currentTimeMillis() > pv.expiresAt()) {
            pendingCodes.remove(name.toLowerCase(Locale.ROOT), pv);
            return false;
        }
        return true;
    }

    /**
     * Returns the remaining personal resend cooldown of the player, in seconds.
     *
     * @param name the player name
     * @return remaining cooldown in seconds, 0 if none
     */
    public long getPersonalCooldownRemainingSeconds(String name) {
        PendingVerification pv = pendingCodes.get(name.toLowerCase(Locale.ROOT));
        if (pv == null) {
            return 0;
        }
        long remainingMs = personalCooldownMs - (System.currentTimeMillis() - pv.lastSentAt());
        return remainingMs <= 0 ? 0 : (remainingMs + 999) / 1000;
    }

    /**
     * Returns the remaining validity of the player's pending verification code, in seconds.
     *
     * @param name the player name
     * @return remaining validity in seconds, 0 if no valid pending code exists
     */
    public long getPendingCodeRemainingSeconds(String name) {
        PendingVerification pv = pendingCodes.get(name.toLowerCase(Locale.ROOT));
        if (pv == null) {
            return 0;
        }
        long remainingMs = pv.expiresAt() - System.currentTimeMillis();
        return remainingMs <= 0 ? 0 : (remainingMs + 999) / 1000;
    }

    /**
     * Generates a verification code and sends it to the given email address,
     * respecting the personal and global send cooldowns.
     *
     * @param playerName the player to send the code to
     * @param email the email address to send the code to
     * @return the outcome of the send request
     */
    public SendCodeResult sendCode(String playerName, String email) {
        String name = playerName.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        PendingVerification existing = pendingCodes.get(name);
        if (existing != null && now - existing.lastSentAt() < personalCooldownMs) {
            return SendCodeResult.PERSONAL_COOLDOWN;
        }

        long lastGlobal = lastGlobalSendAt.get();
        if (now - lastGlobal < globalCooldownMs
            || !lastGlobalSendAt.compareAndSet(lastGlobal, now)) {
            return SendCodeResult.GLOBAL_COOLDOWN;
        }

        String code = RandomStringUtils.generateNum(codeLength);
        if (!emailService.sendEmailVerificationMail(playerName, email, code, codeValidityMinutes)) {
            // Roll back the global timestamp so other players are not blocked by our failure
            lastGlobalSendAt.compareAndSet(now, lastGlobal);
            return SendCodeResult.SEND_FAILED;
        }

        pendingCodes.put(name, new PendingVerification(code, now + codeValidityMs, 0, now));
        return SendCodeResult.SENT;
    }

    /**
     * Verifies the code submitted by the player.
     *
     * @param playerName the player name
     * @param code the submitted code
     * @return the verification outcome
     */
    public VerifyResult verifyCode(String playerName, String code) {
        String name = playerName.toLowerCase(Locale.ROOT);
        PendingVerification pv = pendingCodes.get(name);
        if (pv == null || System.currentTimeMillis() > pv.expiresAt()) {
            return VerifyResult.EXPIRED_OR_NONE;
        }
        if (!pv.code().equals(code)) {
            int attempts = pv.attempts() + 1;
            pendingCodes.put(name, new PendingVerification(pv.code(), pv.expiresAt(), attempts, pv.lastSentAt()));
            if (maxAttempts > 0 && attempts >= maxAttempts) {
                pendingCodes.remove(name);
                return VerifyResult.MAX_ATTEMPTS;
            }
            return VerifyResult.WRONG_CODE;
        }
        pendingCodes.remove(name);
        persistVerified(name);
        return VerifyResult.SUCCESS;
    }

    /**
     * Returns the number of wrong attempts still allowed for the player, or -1 if unlimited.
     *
     * @param name the player name
     * @return attempts remaining, or -1 when no limit is configured
     */
    public int getAttemptsRemaining(String name) {
        if (maxAttempts <= 0) {
            return -1;
        }
        PendingVerification pv = pendingCodes.get(name.toLowerCase(Locale.ROOT));
        int attempts = pv == null ? 0 : pv.attempts();
        return Math.max(0, maxAttempts - attempts);
    }

    /**
     * Changes the player's email address and sends a verification code to the new address.
     * Subject to the personal send cooldown: while it is active the request is rejected
     * and the address is left unchanged.
     *
     * @param playerName the player name
     * @param newEmail the new email address
     * @return the outcome of the send request to the new address
     */
    public SendCodeResult changeEmailAndResend(String playerName, String newEmail) {
        String name = playerName.toLowerCase(Locale.ROOT);
        PendingVerification existing = pendingCodes.get(name);
        if (existing != null && System.currentTimeMillis() - existing.lastSentAt() < personalCooldownMs) {
            return SendCodeResult.PERSONAL_COOLDOWN;
        }
        PlayerAuth auth = getAuth(name);
        if (auth == null) {
            return SendCodeResult.SEND_FAILED;
        }
        auth.setEmail(newEmail);
        if (!dataSource.updateEmail(auth)) {
            return SendCodeResult.SEND_FAILED;
        }
        updatePlayerCacheIfPresent(name, auth);
        pendingCodes.remove(name); // the old code for the old address is invalidated
        return sendCode(playerName, newEmail);
    }

    /**
     * Admin action: marks the player's email as verified without touching the address.
     *
     * @param playerName the player name
     */
    public void markVerified(String playerName) {
        persistVerified(playerName.toLowerCase(Locale.ROOT));
    }

    /**
     * Admin action: force-sets an email address and marks it as verified.
     *
     * @param playerName the player name
     * @param email the email address to set
     */
    public void forceSetEmail(String playerName, String email) {
        String name = playerName.toLowerCase(Locale.ROOT);
        PlayerAuth auth = getAuth(name);
        if (auth == null) {
            return;
        }
        auth.setEmail(email);
        auth.setEmailVerified(true);
        dataSource.updateEmail(auth);
        dataSource.updateEmailVerified(auth);
        updatePlayerCacheIfPresent(name, auth);
        pendingCodes.remove(name);
    }

    /**
     * Admin action: resets the verified flag.
     *
     * @param playerName the player name
     */
    public void unverify(String playerName) {
        String name = playerName.toLowerCase(Locale.ROOT);
        PlayerAuth auth = getAuth(name);
        if (auth == null) {
            return;
        }
        auth.setEmailVerified(false);
        dataSource.updateEmailVerified(auth);
        updatePlayerCacheIfPresent(name, auth);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    @VisibleForTesting
    String getPendingCodeForTesting(String name) {
        PendingVerification pv = pendingCodes.get(name.toLowerCase(Locale.ROOT));
        return pv == null ? null : pv.code();
    }

    private void persistVerified(String name) {
        PlayerAuth auth = getAuth(name);
        if (auth == null) {
            return;
        }
        auth.setEmailVerified(true);
        dataSource.updateEmailVerified(auth);
        updatePlayerCacheIfPresent(name, auth);
    }

    private PlayerAuth getAuth(String name) {
        PlayerAuth auth = playerCache.getAuth(name);
        return auth != null ? auth : dataSource.getAuth(name);
    }

    /**
     * Updates the cache only for players who are already cached. Admin edits can target offline
     * accounts, and a PlayerCache entry means the player is treated as authenticated.
     */
    private void updatePlayerCacheIfPresent(String name, PlayerAuth auth) {
        if (playerCache.getAuth(name) != null) {
            playerCache.updatePlayer(auth);
        }
    }

    @Override
    public void reload(Settings settings) {
        this.active = settings.getProperty(EmailSettings.VERIFICATION_ENABLED)
            && emailService.hasAllInformation();
        this.codeLength = settings.getProperty(EmailSettings.VERIFICATION_CODE_LENGTH);
        this.codeValidityMinutes = settings.getProperty(EmailSettings.VERIFICATION_CODE_VALIDITY_MINUTES);
        this.codeValidityMs = codeValidityMinutes * 60_000L;
        this.personalCooldownMs = settings.getProperty(EmailSettings.VERIFICATION_PERSONAL_COOLDOWN_MS);
        this.globalCooldownMs = settings.getProperty(EmailSettings.VERIFICATION_GLOBAL_COOLDOWN_MS);
        this.maxAttempts = settings.getProperty(EmailSettings.VERIFICATION_MAX_ATTEMPTS);
    }

    @Override
    public void performCleanup() {
        long now = System.currentTimeMillis();
        pendingCodes.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }

    /**
     * Pending verification code state of a player.
     */
    public record PendingVerification(String code, long expiresAt, int attempts, long lastSentAt) { }

    /**
     * Outcome of a code send request.
     */
    public enum SendCodeResult {
        /** Code was generated and the mail was sent. */
        SENT,
        /** The player's personal cooldown is still active. */
        PERSONAL_COOLDOWN,
        /** The global send cooldown is still active. */
        GLOBAL_COOLDOWN,
        /** The mail could not be sent. */
        SEND_FAILED
    }

    /**
     * Outcome of a code verification attempt.
     */
    public enum VerifyResult {
        /** The code was correct; the email is now marked as verified. */
        SUCCESS,
        /** The code was wrong, but attempts remain. */
        WRONG_CODE,
        /** The code was wrong and the attempt limit was reached. */
        MAX_ATTEMPTS,
        /** No pending code exists, or it has expired. */
        EXPIRED_OR_NONE
    }
}
