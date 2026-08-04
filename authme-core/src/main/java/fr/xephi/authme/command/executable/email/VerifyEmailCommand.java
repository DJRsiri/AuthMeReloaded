package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.process.email.AsyncEmailVerification;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.EmailVerificationGate;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;
import java.util.Locale;

/**
 * Command for submitting and resending email verification codes ({@code /email verify}).
 */
public class VerifyEmailCommand extends PlayerCommand {

    @Inject
    private Management management;

    @Inject
    private CommonService commonService;

    @Inject
    private AsyncEmailVerification asyncEmailVerification;

    @Inject
    private EmailVerificationGate gate;

    @Override
    public void runCommand(Player player, List<String> arguments) {
        String name = player.getName().toLowerCase(Locale.ROOT);
        if (!asyncEmailVerification.mayUse(player)) {
            commonService.send(player, MessageKey.USAGE_EMAIL_VERIFY);
            return;
        }

        if (arguments.isEmpty() || "resend".equalsIgnoreCase(arguments.get(0))) {
            management.performEmailVerification(player, "resend", null);
            return;
        }

        String sub = arguments.get(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "setemail":
                if (arguments.size() < 2) {
                    commonService.send(player, MessageKey.USAGE_EMAIL_VERIFY);
                } else {
                    management.performEmailVerification(player, "setemail", arguments.get(1));
                }
                break;
            case "cancel":
                if (gate.isGated(name)) {
                    gate.kickFromGate(player, MessageKey.EMAIL_VERIFICATION_CANCEL_KICK);
                }
                break;
            case "change":
            case "back":
                // Handled by the verification dialogs where supported; nothing to do in chat
                break;
            default:
                management.performEmailVerification(player, "submit", arguments.get(0));
        }
    }

    @Override
    public MessageKey getArgumentsMismatchMessage() {
        return MessageKey.USAGE_EMAIL_VERIFY;
    }
}
