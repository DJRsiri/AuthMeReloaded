package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.EmailVerificationGate;
import fr.xephi.authme.service.EmailVerificationService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

/**
 * Admin command to manage players' email verification state:
 * {@code /authme emailverify bypass|set|unverify <player> [email]}.
 */
public class EmailVerifyAdminCommand implements ExecutableCommand {

    @Inject
    private DataSource dataSource;

    @Inject
    private CommonService commonService;

    @Inject
    private EmailVerificationService verificationService;

    @Inject
    private EmailVerificationGate gate;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private LimboService limboService;

    @Inject
    private ValidationService validationService;

    @Inject
    private PlayerCache playerCache;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        String action = arguments.get(0).toLowerCase(Locale.ROOT);
        String name = arguments.get(1);
        switch (action) {
            case "bypass":
                performBypass(sender, name);
                break;
            case "set":
                if (arguments.size() < 3) {
                    commonService.send(sender, MessageKey.UNKNOWN_COMMAND);
                } else {
                    performSet(sender, name, arguments.get(2));
                }
                break;
            case "unverify":
                performUnverify(sender, name);
                break;
            default:
                commonService.send(sender, MessageKey.UNKNOWN_COMMAND);
        }
    }

    private void performBypass(CommandSender sender, String name) {
        bukkitService.runTaskAsynchronously(() -> {
            if (!dataSource.isAuthAvailable(name)) {
                commonService.send(sender, MessageKey.UNKNOWN_USER);
                return;
            }
            verificationService.markVerified(name);
            commonService.send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_SUCCESS);
            releaseGateIfHeld(name);
        });
    }

    private void performSet(CommandSender sender, String name, String email) {
        if (!validationService.validateEmail(email)) {
            commonService.send(sender, MessageKey.INVALID_EMAIL);
            return;
        }
        bukkitService.runTaskAsynchronously(() -> {
            if (!dataSource.isAuthAvailable(name)) {
                commonService.send(sender, MessageKey.UNKNOWN_USER);
                return;
            }
            verificationService.forceSetEmail(name, email);
            commonService.send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_SUCCESS);
            releaseGateIfHeld(name);
        });
    }

    private void performUnverify(CommandSender sender, String name) {
        bukkitService.runTaskAsynchronously(() -> {
            if (!dataSource.isAuthAvailable(name)) {
                commonService.send(sender, MessageKey.UNKNOWN_USER);
                return;
            }
            verificationService.unverify(name);
            commonService.send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_SUCCESS);
            Player player = bukkitService.getPlayerExact(name);
            if (player != null && !gate.isGated(name) && playerCache.isAuthenticated(name)) {
                // Put the online player back through the verification gate
                bukkitService.scheduleSyncTaskFromOptionallyAsyncTask(player, () -> {
                    limboService.createLimboPlayer(player, true);
                    gate.startGate(player, () -> limboService.restoreData(player));
                });
            }
        });
    }

    private void releaseGateIfHeld(String name) {
        Player player = bukkitService.getPlayerExact(name);
        if (player != null && gate.isGated(name)) {
            gate.completeGate(player);
        }
    }
}
