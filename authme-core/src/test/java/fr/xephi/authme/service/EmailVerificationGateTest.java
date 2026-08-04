package fr.xephi.authme.service;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link EmailVerificationGate}.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationGateTest {

    @Mock
    private EmailVerificationService verificationService;

    @Mock
    private CommonService commonService;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private LimboService limboService;

    @Mock
    private PlayerCache playerCache;

    private EmailVerificationGate gate;

    @BeforeEach
    void createGate() {
        gate = new EmailVerificationGate(verificationService, commonService, bukkitService,
            limboService, playerCache);
    }

    @Test
    void shouldEnforceWhenServiceRequiresVerification() {
        // given
        Player player = mockPlayer("Gated");
        PlayerAuth auth = PlayerAuth.builder().name("gated").email("g@example.com").build();
        given(playerCache.getAuth("gated")).willReturn(auth);
        given(verificationService.isActive()).willReturn(true);
        given(verificationService.isVerificationRequired(auth)).willReturn(true);

        // when / then
        assertThat(gate.shouldEnforce(player), equalTo(true));
    }

    @Test
    void shouldNotEnforceWhenNotRequired() {
        // given
        given(verificationService.isActive()).willReturn(false);

        // when / then
        assertThat(gate.shouldEnforce(mock(Player.class)), equalTo(false));
    }

    @Test
    void shouldNotResendCodeWhenOneIsPending() {
        // given
        Player player = mockPlayer("Pending");
        PlayerAuth auth = PlayerAuth.builder().name("pending").email("p@example.com").build();
        given(playerCache.getAuth("pending")).willReturn(auth);
        given(verificationService.hasPendingCode("pending")).willReturn(true);
        given(commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS)).willReturn(300);
        given(commonService.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);

        // when
        gate.startGate(player, () -> { });

        // then
        verify(verificationService, never()).sendCode(anyString(), anyString());
        assertThat(gate.isGated("pending"), equalTo(true));
    }

    @Test
    void shouldSendCodeMuteAndScheduleTasksOnStart() {
        // given
        Player player = mockPlayer("Newbie");
        PlayerAuth auth = PlayerAuth.builder().name("newbie").email("n@example.com").build();
        given(playerCache.getAuth("newbie")).willReturn(auth);
        given(verificationService.hasPendingCode("newbie")).willReturn(false);
        given(verificationService.sendCode("Newbie", "n@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SENT);
        given(commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS)).willReturn(300);
        given(commonService.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);
        Runnable resume = mock(Runnable.class);

        // when
        gate.startGate(player, resume);

        // then
        verify(limboService).muteMessageTask(player);
        verify(verificationService).sendCode("Newbie", "n@example.com");
        verify(bukkitService).runTaskLater(eq(player), any(Runnable.class), eq(300L * 20L));
        verify(bukkitService).runTaskTimer(eq(player), any(Runnable.class), eq(100L), eq(100L));
        verify(resume, never()).run();
        assertThat(gate.isGated("newbie"), equalTo(true));
        verify(commonService).send(player, MessageKey.EMAIL_VERIFICATION_REQUIRED, "n@example.com");
    }

    @Test
    void shouldCancelTasksAndResumeOnComplete() {
        // given
        Player player = mockPlayer("Done");
        PlayerAuth auth = PlayerAuth.builder().name("done").email("d@example.com").build();
        given(playerCache.getAuth("done")).willReturn(auth);
        given(verificationService.hasPendingCode("done")).willReturn(true);
        given(commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS)).willReturn(300);
        given(commonService.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);
        CancellableTask timeoutTask = mock(CancellableTask.class);
        CancellableTask reminderTask = mock(CancellableTask.class);
        given(bukkitService.runTaskLater(eq(player), any(Runnable.class), anyLong())).willReturn(timeoutTask);
        given(bukkitService.runTaskTimer(eq(player), any(Runnable.class), anyLong(), anyLong()))
            .willReturn(reminderTask);
        Runnable resume = mock(Runnable.class);
        gate.startGate(player, resume);

        // when
        gate.completeGate(player);

        // then
        assertThat(gate.isGated("done"), equalTo(false));
        verify(timeoutTask).cancel();
        verify(reminderTask).cancel();
        verify(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(player, resume);
    }

    @Test
    void shouldKickPlayerWhenTimeoutTaskFires() {
        // given
        Player player = mockPlayer("Slow");
        PlayerAuth auth = PlayerAuth.builder().name("slow").email("s@example.com").build();
        given(playerCache.getAuth("slow")).willReturn(auth);
        given(verificationService.hasPendingCode("slow")).willReturn(true);
        given(commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS)).willReturn(300);
        given(commonService.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);
        given(commonService.retrieveSingleMessage(player, MessageKey.EMAIL_VERIFICATION_TIMEOUT_KICK))
            .willReturn("Too slow!");
        gate.startGate(player, () -> { });

        // when: fire the scheduled timeout task
        ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(bukkitService).runTaskLater(eq(player), timeoutCaptor.capture(), anyLong());
        timeoutCaptor.getValue().run();

        // then: a sync kick is scheduled with the timeout message
        assertThat(gate.isGated("slow"), equalTo(false));
        ArgumentCaptor<Runnable> kickCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(eq(player), kickCaptor.capture());
        kickCaptor.getValue().run();
        verify(player).kickPlayer("Too slow!");
    }

    @Test
    void shouldCleanUpSilentlyOnQuit() {
        // given
        Player player = mockPlayer("Quitter");
        PlayerAuth auth = PlayerAuth.builder().name("quitter").email("q@example.com").build();
        given(playerCache.getAuth("quitter")).willReturn(auth);
        given(verificationService.hasPendingCode("quitter")).willReturn(true);
        given(commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS)).willReturn(0);
        given(commonService.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);
        Runnable resume = mock(Runnable.class);
        gate.startGate(player, resume);

        // when
        gate.cancelGateOnQuit("quitter");

        // then
        assertThat(gate.isGated("quitter"), equalTo(false));
        verify(resume, never()).run();
        verify(bukkitService, never()).scheduleSyncTaskFromOptionallyAsyncTask(any(Player.class), any(Runnable.class));
    }

    @Test
    void shouldKeepPlayerInGateOnSendFailureWithRetry() {
        // given
        Player player = mockPlayer("Unlucky");
        PlayerAuth auth = PlayerAuth.builder().name("unlucky").email("u@example.com").build();
        given(playerCache.getAuth("unlucky")).willReturn(auth);
        given(verificationService.hasPendingCode("unlucky")).willReturn(false);
        given(verificationService.sendCode("Unlucky", "u@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SEND_FAILED);
        given(commonService.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION))
            .willReturn(EmailVerificationSendFailureAction.RETRY);
        given(commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS)).willReturn(0);

        // when
        gate.startGate(player, () -> { });

        // then
        assertThat(gate.isGated("unlucky"), equalTo(true));
        verify(commonService).send(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED);
    }

    @Test
    void shouldKickPlayerOnSendFailureWithKick() {
        // given
        Player player = mockPlayer("Kicked");
        PlayerAuth auth = PlayerAuth.builder().name("kicked").email("k@example.com").build();
        given(playerCache.getAuth("kicked")).willReturn(auth);
        given(verificationService.hasPendingCode("kicked")).willReturn(false);
        given(verificationService.sendCode("Kicked", "k@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SEND_FAILED);
        given(commonService.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION))
            .willReturn(EmailVerificationSendFailureAction.KICK);
        given(commonService.retrieveSingleMessage(player, MessageKey.EMAIL_VERIFICATION_SEND_FAILED))
            .willReturn("Mail broken");

        // when
        gate.startGate(player, () -> { });

        // then
        assertThat(gate.isGated("kicked"), equalTo(false));
        ArgumentCaptor<Runnable> kickCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(eq(player), kickCaptor.capture());
        kickCaptor.getValue().run();
        verify(player).kickPlayer("Mail broken");
    }

    @Test
    void shouldResumeImmediatelyOnSendFailureWithAllow() {
        // given
        Player player = mockPlayer("Allowed");
        PlayerAuth auth = PlayerAuth.builder().name("allowed").email("a@example.com").build();
        given(playerCache.getAuth("allowed")).willReturn(auth);
        given(verificationService.hasPendingCode("allowed")).willReturn(false);
        given(verificationService.sendCode("Allowed", "a@example.com"))
            .willReturn(EmailVerificationService.SendCodeResult.SEND_FAILED);
        given(commonService.getProperty(EmailSettings.VERIFICATION_SEND_FAILURE_ACTION))
            .willReturn(EmailVerificationSendFailureAction.ALLOW);
        Runnable resume = mock(Runnable.class);

        // when
        gate.startGate(player, resume);

        // then
        assertThat(gate.isGated("allowed"), equalTo(false));
        verify(bukkitService).scheduleSyncTaskFromOptionallyAsyncTask(player, resume);
    }

    @Test
    void shouldPromptForBindingWhenPlayerHasNoEmail() {
        // given
        Player player = mockPlayer("NoMail");
        PlayerAuth auth = PlayerAuth.builder().name("nomail").build();
        given(playerCache.getAuth("nomail")).willReturn(auth);
        given(commonService.getProperty(EmailSettings.VERIFICATION_TIMEOUT_SECONDS)).willReturn(0);
        given(commonService.getProperty(RegistrationSettings.MESSAGE_INTERVAL)).willReturn(5);

        // when
        gate.startGate(player, () -> { });

        // then: no code is sent (no address), binding hint is shown instead
        verify(verificationService, never()).sendCode(anyString(), anyString());
        verify(commonService).send(player, MessageKey.EMAIL_VERIFICATION_BINDING_REQUIRED);
    }

    private static Player mockPlayer(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }
}
