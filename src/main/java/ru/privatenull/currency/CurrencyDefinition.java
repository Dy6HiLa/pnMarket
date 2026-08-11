package ru.privatenull.currency;

/** Immutable configuration of one market currency. */
public record CurrencyDefinition(String id, String name, String balancePlaceholder,
                                  String withdrawCommand, String depositCommand) {
    private static final java.util.regex.Pattern COMMAND_PLACEHOLDER =
            java.util.regex.Pattern.compile("\\{([^{}]+)}");

    public CurrencyDefinition {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Currency id must contain 1-32 lowercase letters, digits, _ or -");
        }
        name = name == null || name.isBlank() ? id : name;
        balancePlaceholder = balancePlaceholder == null ? "" : balancePlaceholder.trim();
        withdrawCommand = withdrawCommand == null ? "" : withdrawCommand.trim();
        depositCommand = depositCommand == null ? "" : depositCommand.trim();
        validateCommand("withdraw-command", withdrawCommand);
        validateCommand("deposit-command", depositCommand);
    }

    private static void validateCommand(String field, String command) {
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must not contain line breaks");
        }
        java.util.regex.Matcher matcher = COMMAND_PLACEHOLDER.matcher(command);
        while (matcher.find()) {
            if (!java.util.Set.of("player", "uuid", "amount").contains(matcher.group(1))) {
                throw new IllegalArgumentException(field + " contains unsupported placeholder {" + matcher.group(1) + "}");
            }
        }
        String remainder = matcher.reset().replaceAll("");
        if (remainder.contains("{") || remainder.contains("}")) {
            throw new IllegalArgumentException(field + " contains a malformed placeholder");
        }
    }
}
