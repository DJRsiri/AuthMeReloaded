package fr.xephi.authme.service;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link EmailVerificationService}.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private Settings settings;

    @Mock
    private DataSource dataSource;

    @Mock
    private EmailService emailService;

    @Mock
    private PlayerCache playerCache;

    @BeforeEach
    void setUpSettings() {
        given(settings.getProperty(EmailSettings.VERIFICATION_ENABLED)).willReturn(true);
        given(settings.getProperty(EmailSettings.VERIFICATION_CODE_LENGTH)).willReturn(6);
        given(settings.getProperty(EmailSettings.VERIFICATION_CODE_VALIDITY_MINUTES)).willReturn(10);
        given(settings.getProperty(EmailSettings.VERIFICATION_PERSONAL_COOLDOWN_MS)).willReturn(60_000L);
        given(settings.getProperty(EmailSettings.VERIFICATION_GLOBAL_COOLDOWN_MS)).willReturn(1_000L);
        given(settings.getProperty(EmailSettings.VERIFICATION_MAX_ATTEMPTS)).willReturn(5);
        given(emailService.hasAllInformation()).willReturn(true);
    }

    @Test
    void shouldSendCodeSuccessfully() {
        // given
        given(emailService.sendEmailVerificationMail(anyString(), anyString(), anyString(), eq(10)))
            .willReturn(true);
        EmailVerificationService service = createService();

        // when
        EmailVerificationService.SendCodeResult result = service.sendCode("Player", "p@example.com");

        // then
        assertThat(result, equalTo(EmailVerificationService.SendCodeResult.SENT));
        assertThat(service.hasPendingCode("player"), equalTo(true));
        verify(emailService).sendEmailVerificationMail(eq("Player"), eq("p@example.com"), anyString(), eq(10));
    }

    @Test
    void shouldRejectSecondSendWithinPersonalCooldown() {
        // given
        given(emailService.sendEmailVerificationMail(anyString(), anyString(), anyString(), anyInt()))
            .willReturn(true);
        EmailVerificationService service = createService();
        service.sendCode("Player", "p@example.com");

        // when
        EmailVerificationService.SendCodeResult result = service.sendCode("Player", "p@example.com");

        // then
        assertThat(result, equalTo(EmailVerificationService.SendCodeResult.PERSONAL_COOLDOWN));
        assertThat(service.getPersonalCooldownRemainingSeconds("player") > 0, equalTo(true));
        verify(emailService).sendEmailVerificationMail(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void shouldRejectOtherPlayerWithinGlobalCooldown() {
        // given
        given(emailService.sendEmailVerificationMail(anyString(), anyString(), anyString(), anyInt()))
            .willReturn(true);
        EmailVerificationService service = createService();
        service.sendCode("PlayerA", "a@example.com");

        // when: another player sends within the global cooldown window
        EmailVerificationService.SendCodeResult result = service.sendCode("PlayerB", "b@example.com");

        // then
        assertThat(result, equalTo(EmailVerificationService.SendCodeResult.GLOBAL_COOLDOWN));
        assertThat(service.hasPendingCode("playerb"), equalTo(false));
    }

    @Test
    void shouldRollBackGlobalCooldownWhenSendingFails() {
        // given
        given(emailService.sendEmailVerificationMail(anyString(), anyString(), anyString(), anyInt()))
            .willReturn(false);
        EmailVerificationService service = createService();

        // when
        EmailVerificationService.SendCodeResult failure = service.sendCode("PlayerA", "a@example.com");

        // then: failure is reported and another player may send immediately afterwards
        assertThat(failure, equalTo(EmailVerificationService.SendCodeResult.SEND_FAILED));
        given(emailService.sendEmailVerificationMail(anyString(), anyString(), anyString(), anyInt()))
            .willReturn(true);
        EmailVerificationService.SendCodeResult secondTry = service.sendCode("PlayerB", "b@example.com");
        assertThat(secondTry, equalTo(EmailVerificationService.SendCodeResult.SENT));
    }

    @Test
    void shouldVerifyCorrectCodeAndPersistFlag() {
        // given
        given(emailService.sendEmailVerificationMail(anyString(), anyString(), anyString(), anyInt()))
            .willReturn(true);
        PlayerAuth auth = PlayerAuth.builder().name("Player").email("p@example.com").build();
        given(playerCache.getAuth("player")).willReturn(auth);
        given(dataSource.updateEmailVerified(auth)).willReturn(true);
        EmailVerificationService service = createService();
        service.sendCode("Player", "p@example.com");
        String code = service.getPendingCodeForTesting("player");

        // when
        EmailVerificationService.VerifyResult result = service.verifyCode("Player", code);

        // then
        assertThat(result, equalTo(EmailVerificationService.VerifyResult.SUCCESS));
        assertThat(auth.isEmailVerified(), equalTo(true));
        assertThat(service.hasPendingCode("player"), equalTo(false));
        verify(dataSource).updateEmailVerified(auth);
        verify(playerCache).updatePlayer(auth);
    }

    @Test
    void shouldCountWrongAttemptsAndKickAtMaxAttempts() {
        // given
        given(emailService.sendEmailVerificationMail(anyString(), anyString(), anyString(), anyInt()))
            .willReturn(true);
        EmailVerificationService service = createService();
        service.sendCode("Player", "p@example.com");
        String code = service.getPendingCodeForTesting("player");
        String wrongCode = code.equals("000000") ? "000001" : "000000";

        // when / then: 4 wrong attempts, then the 5th reaches the limit
        for (int i = 1; i <= 4; i++) {
            assertThat(service.verifyCode("Player", wrongCode),
                equalTo(EmailVerificationService.VerifyResult.WRONG_CODE));
        }
        assertThat(service.verifyCode("Player", wrongCode),
            equalTo(EmailVerificationService.VerifyResult.MAX_ATTEMPTS));
        assertThat(service.hasPendingCode("player"), equalTo(false));
        verify(dataSource, never()).updateEmailVerified(any());
    }

    @Test
    void shouldReportExpiredOrMissingCode() {
        // given
        EmailVerificationService service = createService();

        // when / then
        assertThat(service.verifyCode("Player", "123456"),
            equalTo(EmailVerificationService.VerifyResult.EXPIRED_OR_NONE));
    }

    @Test
    void shouldChangeEmailAndSendNewCode() {
        // given
        given(settings.getProperty(EmailSettings.VERIFICATION_GLOBAL_COOLDOWN_MS)).willReturn(0L);
        given(emailService.sendEmailVerificationMail(anyString(), anyString(), anyString(), anyInt()))
            .willReturn(true);
        PlayerAuth auth = PlayerAuth.builder().name("Player").email("old@example.com").build();
        given(playerCache.getAuth("player")).willReturn(auth);
        given(dataSource.updateEmail(auth)).willReturn(true);
        EmailVerificationService service = createService();
        service.sendCode("Player", "old@example.com");

        // when
        EmailVerificationService.SendCodeResult result = service.changeEmailAndResend("Player", "new@example.com");

        // then
        assertThat(result, equalTo(EmailVerificationService.SendCodeResult.SENT));
        assertThat(auth.getEmail(), equalTo("new@example.com"));
        verify(dataSource).updateEmail(auth);
        verify(emailService).sendEmailVerificationMail(eq("Player"), eq("new@example.com"), anyString(), eq(10));
    }

    @Test
    void shouldDetermineWhetherVerificationIsRequired() {
        // given
        EmailVerificationService service = createService();
        PlayerAuth unverified = PlayerAuth.builder().name("A").build();
        PlayerAuth verified = PlayerAuth.builder().name("B").emailVerified(true).build();

        // when / then
        assertThat(service.isVerificationRequired(unverified), equalTo(true));
        assertThat(service.isVerificationRequired(verified), equalTo(false));
        assertThat(service.isVerificationRequired(null), equalTo(false));
    }

    @Test
    void shouldBeInactiveWhenDisabledOrMailIncomplete() {
        // given
        given(settings.getProperty(EmailSettings.VERIFICATION_ENABLED)).willReturn(false);
        EmailVerificationService disabledService = createService();

        given(settings.getProperty(EmailSettings.VERIFICATION_ENABLED)).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(false);
        EmailVerificationService incompleteMailService = createService();

        // when / then
        assertThat(disabledService.isActive(), equalTo(false));
        assertThat(incompleteMailService.isActive(), equalTo(false));
    }

    @Test
    void shouldSupportAdminOperations() {
        // given
        PlayerAuth auth = PlayerAuth.builder().name("Player").email("old@example.com").build();
        given(playerCache.getAuth("player")).willReturn(auth);
        EmailVerificationService service = createService();

        // when: forceSetEmail sets address and verified flag
        service.forceSetEmail("Player", "forced@example.com");

        // then
        assertThat(auth.getEmail(), equalTo("forced@example.com"));
        assertThat(auth.isEmailVerified(), equalTo(true));
        verify(dataSource).updateEmail(auth);
        verify(dataSource).updateEmailVerified(auth);

        // when: unverify resets the flag
        service.unverify("Player");

        // then
        assertThat(auth.isEmailVerified(), equalTo(false));
    }

    private EmailVerificationService createService() {
        return new EmailVerificationService(settings, dataSource, emailService, playerCache);
    }
}
