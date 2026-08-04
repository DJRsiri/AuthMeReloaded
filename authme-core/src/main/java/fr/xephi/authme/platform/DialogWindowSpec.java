package fr.xephi.authme.platform;

import java.util.List;
import java.util.Objects;

/**
 * Resolved dialog text and UX options shared across platform-specific renderers.
 *
 * @param title the dialog title
 * @param inputs the dialog inputs
 * @param primaryButtonLabel the main action button label
 * @param secondaryButtonLabel the optional secondary button label
 * @param showSecondaryButton whether a secondary button should be rendered
 * @param canCloseWithEscape whether the dialog may be dismissed with escape when the platform supports it
 * @param secondaryButtonCommand command template the secondary button should execute in post-join dialogs
 *                               (null for pre-join dialogs where the secondary button is a cancel action)
 * @param body optional translated body text shown below the title (null = no body)
 * @param extraButtons additional buttons rendered after the primary one (may be empty)
 */
public record DialogWindowSpec(String title,
                               List<DialogInputSpec> inputs,
                               String primaryButtonLabel,
                               String secondaryButtonLabel,
                               boolean showSecondaryButton,
                               boolean canCloseWithEscape,
                               String secondaryButtonCommand,
                               String body,
                               List<DialogButtonSpec> extraButtons) {

    public DialogWindowSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(primaryButtonLabel, "primaryButtonLabel");
        Objects.requireNonNull(secondaryButtonLabel, "secondaryButtonLabel");
        inputs = List.copyOf(inputs);
        extraButtons = List.copyOf(extraButtons);
    }

    /** Convenience constructor without body text and extra buttons. */
    public DialogWindowSpec(String title,
                            List<DialogInputSpec> inputs,
                            String primaryButtonLabel,
                            String secondaryButtonLabel,
                            boolean showSecondaryButton,
                            boolean canCloseWithEscape,
                            String secondaryButtonCommand) {
        this(title, inputs, primaryButtonLabel, secondaryButtonLabel,
            showSecondaryButton, canCloseWithEscape, secondaryButtonCommand, null, List.of());
    }

    /** Convenience constructor without extra buttons. */
    public DialogWindowSpec(String title,
                            List<DialogInputSpec> inputs,
                            String primaryButtonLabel,
                            String secondaryButtonLabel,
                            boolean showSecondaryButton,
                            boolean canCloseWithEscape,
                            String secondaryButtonCommand,
                            String body) {
        this(title, inputs, primaryButtonLabel, secondaryButtonLabel,
            showSecondaryButton, canCloseWithEscape, secondaryButtonCommand, body, List.of());
    }

    /** Returns a copy of this spec with the given body text (may be null). */
    public DialogWindowSpec withBody(String newBody) {
        return new DialogWindowSpec(title, inputs, primaryButtonLabel, secondaryButtonLabel,
            showSecondaryButton, canCloseWithEscape, secondaryButtonCommand, newBody, extraButtons);
    }
}
