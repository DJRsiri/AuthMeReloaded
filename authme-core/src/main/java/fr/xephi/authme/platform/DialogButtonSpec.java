package fr.xephi.authme.platform;

import java.util.Objects;

/**
 * A button in a dialog: label plus the command template executed on click
 * (inputs are referenced as $(inputId)).
 *
 * @param label the translated button label
 * @param commandTemplate the command template run when the button is clicked
 */
public record DialogButtonSpec(String label, String commandTemplate) {

    public DialogButtonSpec {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(commandTemplate, "commandTemplate");
    }
}
