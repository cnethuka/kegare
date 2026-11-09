package com.cnethuka.kegare.database;

import java.util.regex.Pattern;

/** What may be said about a database failure: its kind and message with anything that looks like a credential taken out. */
final class DatabaseLog {
    private static final Pattern IN_URL = Pattern.compile("(?i)(password|pwd)=[^&\\s]*");

    private DatabaseLog() {}

    static String describe(Throwable failure, DatabaseConfig config) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (!config.password().isEmpty()) message = message.replace(config.password(), "***");
        message = IN_URL.matcher(message).replaceAll("$1=***");
        return failure.getClass().getSimpleName() + ": " + message;
    }
}
