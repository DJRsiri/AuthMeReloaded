package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.process.email.AsyncEmailVerification;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.EmailVerificationGate;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link VerifyEmailCommand}.
 */
@ExtendWith(MockitoExtension.class)
class VerifyEmailCommandTest {

    @InjectMocks
    private VerifyEmailCommand command;

    @Mock
    private Management management;

    @Mock
    private CommonService commonService;

    @Mock
    private AsyncEmailVerification asyncEmailVerification;

    @Mock
    private EmailVerificationGate gate;

    @Test
    void shouldShowUsageWhenPlayerMayNotUseCommand() {
        // given
        Player player = mockPlayer("Bobby");
        given(asyncEmailVerification.mayUse(player)).willReturn(false);

        // when
        command.runCommand(player, Collections.singletonList("123456"));

        // then
        verify(commonService).send(player, MessageKey.USAGE_EMAIL_VERIFY);
        verifyNoInteractions(management);
    }

    @Test
    void shouldResendWhenNoArgumentsGiven() {
        // given
        Player player = mockPlayer("Bobby");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);

        // when
        command.runCommand(player, Collections.emptyList());

        // then
        verify(management).performEmailVerification(player, "resend", null);
    }

    @Test
    void shouldResendForResendArgument() {
        // given
        Player player = mockPlayer("Bobby");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);

        // when
        command.runCommand(player, Collections.singletonList("resend"));

        // then
        verify(management).performEmailVerification(player, "resend", null);
    }

    @Test
    void shouldShowUsageForSetemailWithoutAddress() {
        // given
        Player player = mockPlayer("Bobby");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);

        // when
        command.runCommand(player, Collections.singletonList("setemail"));

        // then
        verify(commonService).send(player, MessageKey.USAGE_EMAIL_VERIFY);
        verifyNoInteractions(management);
    }

    @Test
    void shouldSetEmailForSetemailArgument() {
        // given
        Player player = mockPlayer("Bobby");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);
        List<String> arguments = Arrays.asList("setemail", "new@example.com");

        // when
        command.runCommand(player, arguments);

        // then
        verify(management).performEmailVerification(player, "setemail", "new@example.com");
    }

    @Test
    void shouldKickGatedPlayerOnCancel() {
        // given
        Player player = mockPlayer("Gated");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);
        given(gate.isGated("gated")).willReturn(true);

        // when
        command.runCommand(player, Collections.singletonList("cancel"));

        // then
        verify(gate).kickFromGate(player, MessageKey.EMAIL_VERIFICATION_CANCEL_KICK);
    }

    @Test
    void shouldIgnoreCancelOutsideGate() {
        // given
        Player player = mockPlayer("Bobby");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);
        given(gate.isGated("bobby")).willReturn(false);

        // when
        command.runCommand(player, Collections.singletonList("cancel"));

        // then
        verifyNoInteractions(management, commonService);
    }

    @Test
    void shouldSubmitCodeForOtherArguments() {
        // given
        Player player = mockPlayer("Bobby");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);

        // when
        command.runCommand(player, Collections.singletonList("123456"));

        // then
        verify(management).performEmailVerification(player, "submit", "123456");
    }

    @Test
    void shouldShowChangeEmailDialogForChangeArgument() {
        // given
        Player player = mockPlayer("Gated");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);
        given(gate.showChangeEmailDialog(player)).willReturn(true);

        // when
        command.runCommand(player, Collections.singletonList("change"));

        // then
        verify(gate).showChangeEmailDialog(player);
        verifyNoInteractions(management, commonService);
    }

    @Test
    void shouldShowUsageWhenChangeDialogUnavailable() {
        // given
        Player player = mockPlayer("Bobby");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);
        given(gate.showChangeEmailDialog(player)).willReturn(false);

        // when
        command.runCommand(player, Collections.singletonList("change"));

        // then
        verify(commonService).send(player, MessageKey.USAGE_EMAIL_VERIFY);
        verifyNoInteractions(management);
    }

    @Test
    void shouldReshowGateDialogForBackArgument() {
        // given
        Player player = mockPlayer("Gated");
        given(asyncEmailVerification.mayUse(player)).willReturn(true);

        // when
        command.runCommand(player, Collections.singletonList("back"));

        // then
        verify(gate).showGateDialogAgain(player);
        verifyNoInteractions(management, commonService);
    }

    private static Player mockPlayer(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }
}
