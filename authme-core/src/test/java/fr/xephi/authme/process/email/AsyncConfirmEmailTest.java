package fr.xephi.authme.process.email;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.PendingEmailVerificationCache;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link AsyncConfirmEmail}.
 */
@ExtendWith(MockitoExtension.class)
class AsyncConfirmEmailTest {

    @InjectMocks
    private AsyncConfirmEmail asyncConfirmEmail;

    @Mock
    private Player player;

    @Mock
    private DataSource dataSource;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private CommonService service;

    @Mock
    private PendingEmailVerificationCache pendingEmailVerificationCache;

    @BeforeAll
    static void setUp() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldMarkEmailVerifiedOnSuccessfulConfirm() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        given(pendingEmailVerificationCache.getEntry("Bobby"))
            .willReturn(new PendingEmailVerificationCache.PendingEntry("new@example.com", "123456", Long.MAX_VALUE));
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(dataSource.updateEmail(auth)).willReturn(true);

        // when
        asyncConfirmEmail.confirmEmail(player, "123456");

        // then
        verify(pendingEmailVerificationCache).removePending("Bobby");
        verify(auth).setEmail("new@example.com");
        verify(auth).setEmailVerified(true);
        verify(dataSource).updateEmail(auth);
        verify(dataSource).updateEmailVerified(auth);
        verify(playerCache).updatePlayer(auth);
        verify(service).send(player, MessageKey.EMAIL_CONFIRM_SUCCESS);
    }

    @Test
    void shouldNotMarkVerifiedWhenSaveFails() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        given(pendingEmailVerificationCache.getEntry("Bobby"))
            .willReturn(new PendingEmailVerificationCache.PendingEntry("new@example.com", "123456", Long.MAX_VALUE));
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(dataSource.updateEmail(auth)).willReturn(false);

        // when
        asyncConfirmEmail.confirmEmail(player, "123456");

        // then
        verify(dataSource).updateEmail(auth);
        verify(auth, never()).setEmailVerified(true);
        verify(dataSource, never()).updateEmailVerified(any(PlayerAuth.class));
        verify(service).send(player, MessageKey.ERROR);
    }

    @Test
    void shouldShowLoginMessageWhenNotAuthenticated() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        given(dataSource.isAuthAvailable("Bobby")).willReturn(true);

        // when
        asyncConfirmEmail.confirmEmail(player, "123456");

        // then
        verify(service).send(player, MessageKey.LOGIN_MESSAGE);
        verifyNoConfirmationProcessed();
    }

    @Test
    void shouldShowExpiredMessageWhenNoPendingEntry() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        given(pendingEmailVerificationCache.getEntry("Bobby")).willReturn(null);

        // when
        asyncConfirmEmail.confirmEmail(player, "123456");

        // then
        verify(service).send(player, MessageKey.EMAIL_CONFIRM_CODE_EXPIRED);
        verifyNoConfirmationProcessed();
    }

    @Test
    void shouldShowErrorForWrongCode() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        given(pendingEmailVerificationCache.getEntry("Bobby"))
            .willReturn(new PendingEmailVerificationCache.PendingEntry("new@example.com", "123456", Long.MAX_VALUE));

        // when
        asyncConfirmEmail.confirmEmail(player, "000000");

        // then
        verify(service).send(player, MessageKey.EMAIL_CONFIRM_WRONG_CODE);
        verifyNoConfirmationProcessed();
    }

    private void verifyNoConfirmationProcessed() {
        verify(pendingEmailVerificationCache, never()).removePending(org.mockito.ArgumentMatchers.anyString());
        verify(dataSource, never()).updateEmail(any(PlayerAuth.class));
        verify(dataSource, never()).updateEmailVerified(any(PlayerAuth.class));
    }
}
