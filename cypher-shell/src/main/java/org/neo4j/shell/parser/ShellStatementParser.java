package org.neo4j.shell.parser;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A cypher aware parser which can detect shell commands (:prefixed) or cypher.
 */
public class ShellStatementParser implements StatementParser {

    private static final Pattern shellCmdPattern = Pattern.compile("^\\s*:.+\\s*$");
    private static final char SEMICOLON = ';';
    private static final char BACKSLASH = '\\';
    private static final String LINE_COMMENT_START = "//";
    private static final String LINE_COMMENT_END = "\n";
    private static final String BLOCK_COMMENT_START = "/*";
    private static final String BLOCK_COMMENT_END = "*/";
    private static final char BACKTICK = '`';
    private static final char DOUBLE_QUOTE = '"';
    private static final char SINGLE_QUOTE = '\'';
    private Optional<String> awaitedRightDelimiter;
    private StringBuilder statement;
    private ArrayList<String> parsedStatements;


    public ShellStatementParser() {
        parsedStatements = new ArrayList<>();
        statement = new StringBuilder();
        awaitedRightDelimiter = Optional.empty();
    }

    @Override
    public void parseMoreText(@Nonnull String line) {
        // See if it could possibly be a shell command, only valid if not in a current statement
        if (!statementStarted() && shellCmdPattern.matcher(line).find()) {
            parsedStatements.add(line);
            return;
        }

        // We will guess it is cypher then
        boolean skipNext = false;
        char prev, current = (char) 0;
        for (char c : line.toCharArray()) {
            // append current
            statement.append(c);
            // last char shuffling
            prev = current;
            current = c;

            if (skipNext) {
                // This char is escaped so gets no special treatment
                skipNext = false;
                continue;
            }

            // Handle comments
            if (inComment()) {
                if (isRightDelimiter(prev, current)) {
                    // Then end it
                    awaitedRightDelimiter = Optional.empty();
                    continue;
                }
                // Didn't end the comment, continue
                continue;
            }

            // Handle quotes
            // backslash can escape stuff outside of comments (but inside quotes too!)
            if (c == BACKSLASH) {
                skipNext = true;
                continue;
            }

            if (inQuote()) {
                if (isRightDelimiter(prev, current)) {
                    // Then end it
                    awaitedRightDelimiter = Optional.empty();
                    continue;
                }
                // Didn't end the quote, continue
                continue;
            }

            // Not escaped, not in a quote, not in a comment
            if (c == SEMICOLON) {
                // end current statement
                parsedStatements.add(statement.toString());
                // start a new statement
                statement = new StringBuilder();
                continue;
            }

            // If it's the start of a quote or comment
            awaitedRightDelimiter = getRightDelimiter(prev, current);
        }
    }

    /**
     * @return true if inside a quote, false otherwise
     */
    private boolean inQuote() {
        return awaitedRightDelimiter.isPresent() && !inComment();
    }

    /**
     * @param first character
     * @param last  character
     * @return true if the last two chars ends the current comment, false otherwise
     */
    private boolean isRightDelimiter(char first, char last) {
        if (!awaitedRightDelimiter.isPresent()) {
            return false;
        }
        final String expectedEnd = awaitedRightDelimiter.get();

        if (expectedEnd.length() == 1) {
            return expectedEnd.equals(String.valueOf(last));
        } else {
            return expectedEnd.equals(String.valueOf(first) + last);
        }
    }

    /**
     * @return true if we are currently inside a comment, false otherwise
     */
    private boolean inComment() {
        return awaitedRightDelimiter.isPresent() &&
                (awaitedRightDelimiter.get().equals(LINE_COMMENT_END) ||
                        awaitedRightDelimiter.get().equals(BLOCK_COMMENT_END));
    }

    /**
     * If the last characters start a quote or a comment, this returns the piece of text which will end said quote
     * or comment.
     *
     * @param first character
     * @param last  character
     * @return the matching right delimiter or something empty if not the start of a quote/comment
     */
    @Nonnull
    private Optional<String> getRightDelimiter(char first, char last) {
        // double characters
        final String lastTwoChars = String.valueOf(first) + last;
        switch (lastTwoChars) {
            case LINE_COMMENT_START:
                return Optional.of(LINE_COMMENT_END);
            case BLOCK_COMMENT_START:
                return Optional.of(BLOCK_COMMENT_END);
        }
        // single characters
        switch (last) {
            case BACKTICK:
            case DOUBLE_QUOTE:
            case SINGLE_QUOTE:
                return Optional.of(String.valueOf(last));
        }

        return Optional.empty();
    }

    /**
     * @return true if a statement has begun (non whitespace has been seen) else false
     */
    private boolean statementStarted() {
        return !statement.toString().trim().isEmpty();
    }

    @Override
    public boolean hasStatements() {
        return !parsedStatements.isEmpty();
    }

    @Nonnull
    @Override
    public List<String> consumeStatements() {
        ArrayList<String> result = parsedStatements;
        parsedStatements = new ArrayList<>();
        return result;
    }

    @Override
    public boolean containsText() {
        return !statement.toString().trim().isEmpty();
    }
}
