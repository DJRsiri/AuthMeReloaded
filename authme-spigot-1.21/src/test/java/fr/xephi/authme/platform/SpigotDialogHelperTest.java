package fr.xephi.authme.platform;

import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.action.RunCommandAction;
import net.md_5.bungee.api.dialog.action.StaticAction;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SpigotDialogHelperTest {

    @Test
    public void showLoginDialogSendsCorrectCommandTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Login", "Password", "Login");

        // when
        SpigotDialogHelper.showLoginDialog(player, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("login $(password)"));
    }

    @Test
    public void showLoginDialogIncludesForgotPasswordButtonWhenSpecified() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = new DialogWindowSpec("Login",
            java.util.List.of(
                new DialogInputSpec("password", "Password", 100),
                new DialogInputSpec("email", "Recovery Email", 100)),
            "Login",
            "Forgot Password?",
            true,
            false,
            "email recover $(email)");

        // when
        SpigotDialogHelper.showLoginDialog(player, dialog);

        // then
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(player).showDialog(captor.capture());
        assertThat(captor.getValue().actions().size(), is(2));
        assertThat(((RunCommandAction) captor.getValue().actions().get(0).action()).template(), is("login $(password)"));
        assertThat(((RunCommandAction) captor.getValue().actions().get(1).action()).template(), is("email recover $(email)"));
    }

    @Test
    public void showTotpDialogSendsCorrectCommandTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("2FA", "Code", "Verify");

        // when
        SpigotDialogHelper.showTotpDialog(player, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("2fa code $(code)"));
    }

    @Test
    public void showRegisterDialogWithPasswordAndNoSecondArgSendsPasswordTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Register", "Password", "Register");

        // when
        SpigotDialogHelper.showRegisterDialog(player, RegistrationType.PASSWORD, RegisterSecondaryArgument.NONE, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("register $(password)"));
    }

    @Test
    public void showRegisterDialogWithPasswordAndConfirmationSendsPasswordConfirmTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Register", "Password", "Register");

        // when
        SpigotDialogHelper.showRegisterDialog(player, RegistrationType.PASSWORD,
            RegisterSecondaryArgument.CONFIRMATION, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("register $(password) $(confirm)"));
    }

    @Test
    public void showRegisterDialogWithPasswordAndEmailMandatorySendsPasswordEmailTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Register", "Password", "Register");

        // when
        SpigotDialogHelper.showRegisterDialog(player, RegistrationType.PASSWORD,
            RegisterSecondaryArgument.EMAIL_MANDATORY, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("register $(password) $(email)"));
    }

    @Test
    public void showRegisterDialogWithPasswordAndEmailOptionalSendsPasswordEmailTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Register", "Password", "Register");

        // when
        SpigotDialogHelper.showRegisterDialog(player, RegistrationType.PASSWORD,
            RegisterSecondaryArgument.EMAIL_OPTIONAL, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("register $(password) $(email)"));
    }

    @Test
    public void showRegisterDialogWithEmailAndNoSecondArgSendsEmailTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Register", "Email", "Register");

        // when
        SpigotDialogHelper.showRegisterDialog(player, RegistrationType.EMAIL, RegisterSecondaryArgument.NONE, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("register $(email)"));
    }

    @Test
    public void showRegisterDialogWithEmailAndConfirmationSendsEmailConfirmTemplate() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = createDialogSpec("Register", "Email", "Register");

        // when
        SpigotDialogHelper.showRegisterDialog(player, RegistrationType.EMAIL,
            RegisterSecondaryArgument.CONFIRMATION, dialog);

        // then
        RunCommandAction action = captureRunCommandAction(player);
        assertThat(action.template(), is("register $(email) $(confirm)"));
    }

    @Test
    public void showEmailGateDialogUsesStaticActionForExtraButtonsWithoutVariables() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = new DialogWindowSpec("Verify Email",
            java.util.List.of(new DialogInputSpec("code", "Code", 16)),
            "Verify",
            "Cancel",
            false,
            false,
            null,
            null,
            java.util.List.of(
                new DialogButtonSpec("Resend", "email verify resend"),
                new DialogButtonSpec("Cancel", "email verify cancel")));

        // when
        SpigotDialogHelper.showEmailGateDialog(player, dialog, "email verify $(code)");

        // then: the primary button keeps the command template, static extras use plain run-command clicks
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(player).showDialog(captor.capture());
        assertThat(captor.getValue().actions().size(), is(3));
        assertThat(((RunCommandAction) captor.getValue().actions().get(0).action()).template(),
            is("email verify $(code)"));
        StaticAction resendAction = (StaticAction) captor.getValue().actions().get(1).action();
        assertThat(resendAction.clickEvent().getAction(), is(ClickEvent.Action.RUN_COMMAND));
        assertThat(resendAction.clickEvent().getValue(), is("/email verify resend"));
        StaticAction cancelAction = (StaticAction) captor.getValue().actions().get(2).action();
        assertThat(cancelAction.clickEvent().getValue(), is("/email verify cancel"));
    }

    @Test
    public void showLoginDialogUsesStaticActionForSecondaryCommandWithoutVariables() {
        // given
        Player player = mock(Player.class);
        DialogWindowSpec dialog = new DialogWindowSpec("Login",
            java.util.List.of(new DialogInputSpec("password", "Password", 100)),
            "Login",
            "Forgot Password?",
            true,
            false,
            "email recover");

        // when
        SpigotDialogHelper.showLoginDialog(player, dialog);

        // then
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(player).showDialog(captor.capture());
        StaticAction secondaryAction = (StaticAction) captor.getValue().actions().get(1).action();
        assertThat(secondaryAction.clickEvent().getValue(), is("/email recover"));
    }

    private static RunCommandAction captureRunCommandAction(Player player) {
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(player).showDialog(captor.capture());
        return (RunCommandAction) captor.getValue().actions().get(0).action();
    }

    private static DialogWindowSpec createDialogSpec(String title, String inputLabel, String primaryButtonLabel) {
        return new DialogWindowSpec(title,
            java.util.List.of(new DialogInputSpec("field", inputLabel, 100)),
            primaryButtonLabel,
            "Cancel",
            false,
            false,
            null);
    }
}

