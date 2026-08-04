package fr.xephi.authme.service;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.util.Utils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gate that keeps logged-in players in limbo until their email address is verified.
 * Attach point: start of the post-login sync process; released via {@link #completeGate}.
 */
public class EmailVerificationGate {

    private static final long TICKS_PER_SECOND = 20L;

    private final EmailVerificationService verificationService;
    private final CommonService commonService;
    private final BukkitService bukkitService;
    private final LimboService limboService;
    private final PlayerCache playerCache;

    private final Map<String, GateSession> sessions = new ConcurrentHashMap<>();

    @Inject
    EmailVerificationGate(EmailVerificationService verificationService, CommonService commonService,
                          BukkitService bukkitService, LimboService limboService, PlayerCache playerCache) {
        this.verificationService = verificationService;
        this.commonService = commonService;
        this.bukkitService = bukkitService;
        this.limboService = limboService;
        this.playerCache = playerCache;
    }

    /**
     * Returns whether the player is currently held in the verification gate.
     *
     * @param name the player name
     * @return true if the player is gated
     */
    public boolean isGated(String name) {
        return sessions.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns whether the player must pass the gate before playing.
     *
     * @param player the player to check
     * @return true if the player must pass email verification
     */
    public boolean shouldEnforce(Player player) {
        if (!verificationService.isActive()) {
            return false;
        }
        PlayerAuth auth = playerCache.getAuth(player.getName().toLowerCase(Locale.ROOT));
        return verificationService.isVerificationRequired(auth);
    }

    /**
     * Puts the player into the gate: keeps limbo, sends a code if none is pending,
     * shows the verification UI and starts timeout/reminder tasks.
     *
     * @param player the player to gate
     * @param resumeAction action that completes the login flow, run once on release
     */
    public void startGate(Player player, Runnable resumeAction) {
        String name = player.getName().toLowerCase(Locale.ROOT);
        limboService.muteMessageTask(player);

        PlayerAuth auth = playerCache.getAuth(name);
        boolean bindingRequired = auth == null || Utils.isEmailEmpty(auth.getEmail());
        if (!bindingRequired && !verificationService.hasPendingCode(name)) {
            EmailVerificationService.SendCodeResult result =
                verificationService.sendCode(player.getName(), auth.getEmail());
            if (result == EmailVerificationService.SendCodeResult.SEND_FAILED) {
                handleSendFailure(player, resumeAction);
                return;
            }
        }

        int timeoutSeconds = commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS);
        CancellableTask timeoutTask = timeoutSeconds > 0
            ? bukkitService.runTaskLater(player,
                () -> kickFromGate(player, MessageKey.EMAIL_VERIFICATION_TIMEOUT_KICK),
                timeoutSeconds * TICKS_PER_SECOND)
            : null;

        int reminderInterval = commonService.getProperty(RegistrationSettings.MESSAGE_INTERVAL);
        CancellableTask reminderTask = bukkitService.runTaskTimer(player,
            () -> sendGateHint(player), reminderInterval * TICKS_PER_SECOND, reminderInterval * TICKS_PER_SECOND);

        sessions.put(name, new GateSession(resumeAction, timeoutTask, reminderTask));
        sendGateHint(player);
        showGateDialog(player);
    }

    /**
     * Releases the player: cancels tasks, removes the session and runs the resume action.
     *
     * @param player the player to release
     */
    public void completeGate(Player player) {
        GateSession session = sessions.remove(player.getName().toLowerCase(Locale.ROOT));
        if (session == null) {
            return;
        }
        cancelTasks(session);
        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, session.resumeAction());
    }

    /**
     * Cleans up the player's gate session on quit: no kick, no resume.
     *
     * @param name the player name
     */
    public void cancelGateOnQuit(String name) {
        GateSession session = sessions.remove(name.toLowerCase(Locale.ROOT));
        if (session != null) {
            cancelTasks(session);
        }
    }

    /**
     * Kicks the player out of the gate (timeout, cancel button, max attempts).
     *
     * @param player the player to kick
     * @param messageKey the kick message
     */
    public void kickFromGate(Player player, MessageKey messageKey) {
        cancelGateOnQuit(player.getName());
        String kickMessage = commonService.retrieveSingleMessage(player, messageKey);
        bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> player.kickPlayer(kickMessage));
    }

    private void handleSendFailure(Player player, Runnable resumeAction) {
        switch (commonService.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION)) {
            case KICK:
                kickFromGate(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
                break;
            case ALLOW:
                bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, resumeAction);
                break;
            case RETRY:
            default:
                commonService.send(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
                // Register a session without a code so the player stays in limbo and can
                // trigger a resend via /email verify resend
                int timeoutSeconds = commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS);
                CancellableTask timeoutTask = timeoutSeconds > 0
                    ? bukkitService.runTaskLater(player,
                        () -> kickFromGate(player, MessageKey.EMAIL_VERIFICATION_TIMEOUT_KICK),
                        timeoutSeconds * TICKS_PER_SECOND)
                    : null;
                sessions.put(player.getName().toLowerCase(Locale.ROOT),
                    new GateSession(resumeAction, timeoutTask, null));
        }
    }

    private void sendGateHint(Player player) {
        PlayerAuth auth = playerCache.getAuth(player.getName().toLowerCase(Locale.ROOT));
        if (auth == null || Utils.isEmailEmpty(auth.getEmail())) {
            commonService.send(player, MessageKey.EMAIL_VERIFICATION_BINDING_REQUIRED);
        } else {
            commonService.send(player, MessageKey.EMAIL_VERIFICATION_REQUIRED, auth.getEmail());
        }
    }

    private void showGateDialog(Player player) {
        // Wired in the dialog task: shows the verification / binding dialog when the
        // platform supports dialogs and they are enabled. The chat hint from sendGateHint
        // is the fallback and is always sent.
    }

    private static void cancelTasks(GateSession session) {
        if (session.timeoutTask() != null) {
            session.timeoutTask().cancel();
        }
        if (session.reminderTask() != null) {
            session.reminderTask().cancel();
        }
    }

    private record GateSession(Runnable resumeAction, CancellableTask timeoutTask, CancellableTask reminderTask) { }
}
