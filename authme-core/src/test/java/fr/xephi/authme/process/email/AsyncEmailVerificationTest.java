package fr.xephi.authme.process.email;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.EmailVerificationGate;
import fr.xephi.authme.service.EmailVerificationService;
import fr.xephi.authme.service.EmailVerificationSendFailureAction;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.EmailSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link AsyncEmailVerification}.
 */
@ExtendWith(MockitoExtension.class)
class AsyncEmailVerificationTest {

    @InjectMocks
    private AsyncEmailVerification asyncEmailVerification;

    @Mock
    private CommonService service;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private EmailVerificationService verificationService;

    @Mock
    private EmailVerificationGate gate;

    @Mock
    private ValidationService validationService;

    // mayUse

    @Test
    void shouldNotAllowUseWhenInactive() {
        // given
        given(verificationService.isActive()).willReturn(false);

        // when / then
        assertThat(asyncEmailVerification.mayUse(mock(Player.class)), equalTo(false));
    }

    @Test
    void shouldAllowUseWhenGated() {
        // given
        Player player = mockPlayer("Gated");
        given(verificationService.isActive()).willReturn(true);
        given(gate.isGated("gated")).willReturn(true);

        // when / then
        assertThat(asyncEmailVerification.mayUse(player), equalTo(true));
    }

    @Test
    void shouldAllowUseWhenUnverifiedWithEmail() {
        // given
        Player player = mockPlayer("Bobby");
        given(verificationService.isActive()).willReturn(true);
        given(gate.isGated("bobby")).willReturn(false);
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);

        // when / then
        assertThat(asyncEmailVerification.mayUse(player), equalTo(true));
    }

    @Test
    void shouldNotAllowUseWhenAlreadyVerified() {
        // given
        Player player = mockPlayer("Bobby");
        given(verificationService.isActive()).willReturn(true);
        given(gate.isGated("bobby")).willReturn(false);
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").emailVerified(true).build();
        given(playerCache.getAuth("bobby")).willReturn(auth);

        // when / then
        assertThat(asyncEmailVerification.mayUse(player), equalTo(false));
    }

    @Test
    void shouldNotAllowUseWhenNoEmail() {
        // given
        Player player = mockPlayer("Bobby");
        given(verificationService.isActive()).willReturn(true);
        given(gate.isGated("bobby")).willReturn(false);
        PlayerAuth auth = PlayerAuth.builder().name("bobby").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);

        // when / then
        assertThat(asyncEmailVerification.mayUse(player), equalTo(false));
    }

    // submitCode

    @Test
    void shouldCompleteGateOnSuccessfulSubmit() {
        // given
        Player player = mockPlayer("Bobby");
        given(verificationService.verifyCode("Bobby", "123456"))
            .willReturn(EmailVerificationService.VerifyResult.SUCCESS);
        given(gate.isGated("bobby")).willReturn(true);

        // when
        asyncEmailVerification.submitCode(player, "123456");

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_SUCCESS);
        verify(gate).completeGate(player);
    }

    @Test
    void shouldOnlyConfirmOnSuccessfulSubmitOutsideGate() {
        // given
        Player player = mockPlayer("Bobby");
        given(verificationService.verifyCode("Bobby", "123456"))
            .willReturn(EmailVerificationService.VerifyResult.SUCCESS);
        given(gate.isGated("bobby")).willReturn(false);

        // when
        asyncEmailVerification.submitCode(player, "123456");

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_SUCCESS);
        verify(gate, never()).completeGate(player);
    }

    @Test
    void shouldReportWrongCodeWithRemainingAttempts() {
        // given
        Player player = mockPlayer("Bobby");
        given(verificationService.verifyCode("Bobby", "000000"))
            .willReturn(EmailVerificationService.VerifyResult.WRONG_CODE);
        given(verificationService.getAttemptsRemaining("bobby")).willReturn(3);

        // when
        asyncEmailVerification.submitCode(player, "000000");

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_WRONG_CODE, "3");
    }

    @Test
    void shouldKickOnMaxAttempts() {
        // given
        Player player = mockPlayer("Bobby");
        given(verificationService.verifyCode("Bobby", "000000"))
            .willReturn(EmailVerificationService.VerifyResult.MAX_ATTEMPTS);

        // when
        asyncEmailVerification.submitCode(player, "000000");

        // then
        verify(gate).kickFromGate(player, MessageKey.EMAIL_VERIFICATION_MAX_ATTEMPTS_KICK);
    }

    @Test
    void shouldShowUsageOnExpiredCode() {
        // given
        Player player = mockPlayer("Bobby");
        given(verificationService.verifyCode("Bobby", "000000"))
            .willReturn(EmailVerificationService.VerifyResult.EXPIRED_OR_NONE);

        // when
        asyncEmailVerification.submitCode(player, "000000");

        // then
        verify(service).send(player, MessageKey.USAGE_EMAIL_VERIFY);
    }

    // resend

    @Test
    void shouldRequireBindingWhenNoEmailOnResend() {
        // given
        Player player = mockPlayer("Bobby");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);

        // when
        asyncEmailVerification.resend(player);

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_BINDING_REQUIRED);
        verify(verificationService, never()).sendCode(anyString(), anyString());
    }

    @Test
    void shouldSendCodeOnResend() {
        // given
        Player player = mockPlayer("Bobby");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(verificationService.sendCode("Bobby", "b@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SENT);
        given(service.getProperty(EmailSettings.VERIFICATION_CODE_VALIDITY_MINUTES)).willReturn(10);

        // when
        asyncEmailVerification.resend(player);

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_SENT, "b@example.com", "10");
    }

    @Test
    void shouldReportPersonalCooldownOnResend() {
        // given
        Player player = mockPlayer("Bobby");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(verificationService.sendCode("Bobby", "b@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.PERSONAL_COOLDOWN);
        given(verificationService.getPersonalCooldownRemainingSeconds("bobby")).willReturn(42L);

        // when
        asyncEmailVerification.resend(player);

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_RESEND_COOLDOWN, "42");
    }

    @Test
    void shouldReportGlobalCooldownOnResend() {
        // given
        Player player = mockPlayer("Bobby");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(verificationService.sendCode("Bobby", "b@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.GLOBAL_COOLDOWN);

        // when
        asyncEmailVerification.resend(player);

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_GLOBAL_COOLDOWN);
    }

    @Test
    void shouldReportSendFailureWithRetryAction() {
        // given
        Player player = mockPlayer("Bobby");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(verificationService.sendCode("Bobby", "b@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SEND_FAILED);
        given(service.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION))
            .willReturn(EmailVerificationSendFailureAction.RETRY);

        // when
        asyncEmailVerification.resend(player);

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
        verify(gate, never()).completeGate(player);
    }

    @Test
    void shouldKickOnSendFailureWithKickAction() {
        // given
        Player player = mockPlayer("Bobby");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(verificationService.sendCode("Bobby", "b@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SEND_FAILED);
        given(service.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION))
            .willReturn(EmailVerificationSendFailureAction.KICK);

        // when
        asyncEmailVerification.resend(player);

        // then
        verify(gate).kickFromGate(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
    }

    @Test
    void shouldReleaseGateOnSendFailureWithAllowAction() {
        // given
        Player player = mockPlayer("Bobby");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(verificationService.sendCode("Bobby", "b@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SEND_FAILED);
        given(service.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION))
            .willReturn(EmailVerificationSendFailureAction.ALLOW);
        given(gate.isGated("bobby")).willReturn(true);

        // when
        asyncEmailVerification.resend(player);

        // then
        verify(gate).completeGate(player);
    }

    @Test
    void shouldOnlyReportSendFailureWithAllowActionOutsideGate() {
        // given
        Player player = mockPlayer("Bobby");
        PlayerAuth auth = PlayerAuth.builder().name("bobby").email("b@example.com").build();
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(verificationService.sendCode("Bobby", "b@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SEND_FAILED);
        given(service.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION))
            .willReturn(EmailVerificationSendFailureAction.ALLOW);
        given(gate.isGated("bobby")).willReturn(false);

        // when
        asyncEmailVerification.resend(player);

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
        verify(gate, never()).completeGate(player);
    }

    // setEmail

    @Test
    void shouldRejectInvalidEmailOnSet() {
        // given
        Player player = mock(Player.class);
        given(validationService.validateEmail("not-an-email")).willReturn(false);

        // when
        asyncEmailVerification.setEmail(player, "not-an-email");

        // then
        verify(service).send(player, MessageKey.INVALID_EMAIL);
        verify(verificationService, never()).changeEmailAndResend(anyString(), anyString());
    }

    @Test
    void shouldChangeEmailAndSendCode() {
        // given
        Player player = mockPlayer("Bobby");
        given(validationService.validateEmail("new@example.com")).willReturn(true);
        given(verificationService.changeEmailAndResend("Bobby", "new@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SENT);
        given(service.getProperty(EmailSettings.VERIFICATION_CODE_VALIDITY_MINUTES)).willReturn(10);

        // when
        asyncEmailVerification.setEmail(player, "new@example.com");

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_EMAIL_CHANGED, "new@example.com");
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_SENT, "new@example.com", "10");
    }

    @Test
    void shouldReportCooldownAfterEmailChange() {
        // given
        Player player = mockPlayer("Bobby");
        given(validationService.validateEmail("new@example.com")).willReturn(true);
        given(verificationService.changeEmailAndResend("Bobby", "new@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.PERSONAL_COOLDOWN);
        given(verificationService.getPersonalCooldownRemainingSeconds("bobby")).willReturn(7L);

        // when
        asyncEmailVerification.setEmail(player, "new@example.com");

        // then
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_EMAIL_CHANGED, "new@example.com");
        verify(service).send(player, MessageKey.EMAIL_VERIFICATION_RESEND_COOLDOWN, "7");
    }

    private static Player mockPlayer(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }
}
