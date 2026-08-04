package fr.xephi.authme.command;

import java.util.List;

/**
 * Wrapper for the description of a command argument.
 */
public class CommandArgumentDescription {

    /**
     * Argument name (one-word description of the argument).
     */
    private final String name;
    /**
     * Argument description.
     */
    private final String description;
    /**
     * Defines whether the argument is optional.
     */
    private final boolean isOptional;
    /**
     * Static tab-completion suggestions for the argument value.
     */
    private final List<String> suggestions;
    /**
     * Whether online player names are offered as tab-completion suggestions.
     */
    private final boolean suggestOnlinePlayers;

    /**
     * Constructor.
     *
     * @param name        The argument name.
     * @param description The argument description.
     * @param isOptional  True if the argument is optional, false otherwise.
     */
    public CommandArgumentDescription(String name, String description, boolean isOptional) {
        this(name, description, isOptional, List.of(), false);
    }

    private CommandArgumentDescription(String name, String description, boolean isOptional,
                                       List<String> suggestions, boolean suggestOnlinePlayers) {
        this.name = name;
        this.description = description;
        this.isOptional = isOptional;
        this.suggestions = suggestions;
        this.suggestOnlinePlayers = suggestOnlinePlayers;
    }

    /**
     * Get the argument name.
     *
     * @return Argument name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the argument description.
     *
     * @return Argument description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Return whether the argument is optional.
     *
     * @return True if the argument is optional, false otherwise.
     */
    public boolean isOptional() {
        return isOptional;
    }

    /**
     * @return static tab-completion suggestions, empty if none
     */
    public List<String> getSuggestions() {
        return suggestions;
    }

    /**
     * @return true if online player names should be suggested for this argument
     */
    public boolean hasOnlinePlayerSuggestions() {
        return suggestOnlinePlayers;
    }

    /**
     * Returns a copy of this argument description with the given static tab-completion suggestions.
     *
     * @param suggestions the values to suggest
     * @return a copy of this description with the suggestions set
     */
    public CommandArgumentDescription withSuggestions(String... suggestions) {
        return new CommandArgumentDescription(name, description, isOptional,
            List.of(suggestions), suggestOnlinePlayers);
    }

    /**
     * Returns a copy of this argument description with online player name suggestions enabled.
     *
     * @return a copy of this description with online player suggestions
     */
    public CommandArgumentDescription withOnlinePlayerSuggestions() {
        return new CommandArgumentDescription(name, description, isOptional, suggestions, true);
    }

}
