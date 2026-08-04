package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.EmailVerificationGate;
import fr.xephi.authme.service.EmailVerificationService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskAsynchronously;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link EmailVerifyAdminCommand}.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerifyAdminCommandTest {

    @InjectMocks
    private EmailVerifyAdminCommand command;

    @Mock
    private DataSource dataSource;

    @Mock
    private CommonService commonService;

    @Mock
    private EmailVerificationService verificationService;

    @Mock
    private EmailVerificationGate gate;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private LimboService limboService;

    @Mock
    private ValidationService validationService;

    @Mock
    private PlayerCache playerCache;

    @Test
    void shouldBypassVerificationAndReleaseGatedPlayer() {
        // given
        CommandSender sender = mock(CommandSender.class);
        Player player = mock(Player.class);
        given(dataSource.isAuthAvailable("bobby")).willReturn(true);
        given(bukkitService.getPlayerExact("bobby")).willReturn(player);
        given(gate.isGated("bobby")).willReturn(true);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("bypass", "bobby"));

        // then
        verify(verificationService).markVerified("bobby");
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_SUCCESS);
        verify(gate).completeGate(player);
    }

    @Test
    void shouldBypassWithoutReleaseWhenPlayerIsOffline() {
        // given
        CommandSender sender = mock(CommandSender.class);
        given(dataSource.isAuthAvailable("bobby")).willReturn(true);
        given(bukkitService.getPlayerExact("bobby")).willReturn(null);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("bypass", "bobby"));

        // then
        verify(verificationService).markVerified("bobby");
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_SUCCESS);
        verify(gate, never()).completeGate(any(Player.class));
    }

    @Test
    void shouldForceSetEmailAndMarkVerified() {
        // given
        CommandSender sender = mock(CommandSender.class);
        given(validationService.validateEmail("new@example.com")).willReturn(true);
        given(dataSource.isAuthAvailable("bobby")).willReturn(true);
        given(bukkitService.getPlayerExact("bobby")).willReturn(null);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("set", "bobby", "new@example.com"));

        // then
        verify(verificationService).forceSetEmail("bobby", "new@example.com");
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_SUCCESS);
    }

    @Test
    void shouldRejectInvalidEmailOnSet() {
        // given
        CommandSender sender = mock(CommandSender.class);
        given(validationService.validateEmail("not-an-email")).willReturn(false);

        // when
        command.executeCommand(sender, Arrays.asList("set", "bobby", "not-an-email"));

        // then
        verify(commonService).send(sender, MessageKey.INVALID_EMAIL);
        verifyNoInteractions(dataSource, verificationService, gate, bukkitService);
    }

    @Test
    void shouldUnverifyAndRegateOnlineAuthenticatedPlayer() {
        // given
        CommandSender sender = mock(CommandSender.class);
        Player player = mock(Player.class);
        given(dataSource.isAuthAvailable("bobby")).willReturn(true);
        given(bukkitService.getPlayerExact("bobby")).willReturn(player);
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        given(gate.isGated("bobby")).willReturn(false);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("unverify", "bobby"));

        // then
        verify(verificationService).unverify("bobby");
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_SUCCESS);
        verify(limboService).createLimboPlayer(player, true);
        ArgumentCaptor<Runnable> resumeCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(gate).startGate(eq(player), resumeCaptor.capture());
        resumeCaptor.getValue().run();
        verify(limboService).restoreData(player);
    }

    @Test
    void shouldNotRegateWhenTargetIsAlreadyGated() {
        // given
        CommandSender sender = mock(CommandSender.class);
        Player player = mock(Player.class);
        given(dataSource.isAuthAvailable("bobby")).willReturn(true);
        given(bukkitService.getPlayerExact("bobby")).willReturn(player);
        given(gate.isGated("bobby")).willReturn(true);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("unverify", "bobby"));

        // then
        verify(verificationService).unverify("bobby");
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_SUCCESS);
        verify(limboService, never()).createLimboPlayer(any(Player.class), eq(true));
        verify(gate, never()).startGate(any(Player.class), any(Runnable.class));
    }

    @Test
    void shouldSendUnknownUserForMissingAuth() {
        // given
        CommandSender sender = mock(CommandSender.class);
        given(dataSource.isAuthAvailable("ghost")).willReturn(false);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("bypass", "ghost"));

        // then
        verify(commonService).send(sender, MessageKey.UNKNOWN_USER);
        verifyNoInteractions(verificationService, gate);
    }

    @Test
    void shouldRejectUnknownAction() {
        // given
        CommandSender sender = mock(CommandSender.class);

        // when
        command.executeCommand(sender, Arrays.asList("foo", "bobby"));

        // then
        verify(commonService).send(sender, MessageKey.UNKNOWN_COMMAND);
        verifyNoInteractions(dataSource, verificationService, gate, bukkitService);
    }

    @Test
    void shouldRejectSetWithoutEmail() {
        // given
        CommandSender sender = mock(CommandSender.class);

        // when
        command.executeCommand(sender, Arrays.asList("set", "bobby"));

        // then
        verify(commonService).send(sender, MessageKey.UNKNOWN_COMMAND);
        verifyNoInteractions(dataSource, verificationService, gate, bukkitService);
    }

    @Test
    void shouldShowStatusForVerifiedPlayerWithPendingCode() {
        // given
        CommandSender sender = mock(CommandSender.class);
        PlayerAuth auth = PlayerAuth.builder().name("bobby").realName("Bobby")
            .email("b@example.com").emailVerified(true).build();
        given(dataSource.getAuth("bobby")).willReturn(auth);
        given(verificationService.getPendingCodeRemainingSeconds("bobby")).willReturn(125L);
        given(verificationService.getPersonalCooldownRemainingSeconds("bobby")).willReturn(30L);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("status", "bobby"));

        // then
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_STATUS,
            "Bobby", "b@example.com", ChatColor.GREEN + "yes");
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_STATUS_PENDING,
            ChatColor.GREEN + "yes (" + ChatColor.WHITE + "125" + ChatColor.GREEN + " sec left)", "30");
    }

    @Test
    void shouldShowStatusForUnverifiedPlayerWithoutEmail() {
        // given
        CommandSender sender = mock(CommandSender.class);
        PlayerAuth auth = PlayerAuth.builder().name("bobby").realName("Bobby").build();
        given(dataSource.getAuth("bobby")).willReturn(auth);
        given(verificationService.getPendingCodeRemainingSeconds("bobby")).willReturn(0L);
        given(verificationService.getPersonalCooldownRemainingSeconds("bobby")).willReturn(0L);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("status", "bobby"));

        // then
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_STATUS,
            "Bobby", ChatColor.RED + "none", ChatColor.RED + "no");
        verify(commonService).send(sender, MessageKey.EMAIL_VERIFICATION_ADMIN_STATUS_PENDING,
            ChatColor.RED + "no", "0");
    }

    @Test
    void shouldSendUnknownUserForStatusOfMissingAuth() {
        // given
        CommandSender sender = mock(CommandSender.class);
        given(dataSource.getAuth("ghost")).willReturn(null);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Arrays.asList("status", "ghost"));

        // then
        verify(commonService).send(sender, MessageKey.UNKNOWN_USER);
        verifyNoInteractions(verificationService, gate);
    }
}
