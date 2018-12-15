package graphql.parser;

import graphql.InvalidSyntaxError;
import graphql.language.SourceLocation;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

public class ExtendedBailStrategy extends BailErrorStrategy {
    private final String input;
    private final String sourceName;

    public ExtendedBailStrategy(String input, String sourceName) {
        this.input = input;
        this.sourceName = sourceName;
    }

    @Override
    public void recover(Parser recognizer, RecognitionException e) {
        try {
            super.recover(recognizer, e);
        } catch (ParseCancellationException parseException) {
            throw mkException(recognizer, e);
        }
    }

    @Override
    public Token recoverInline(Parser recognizer) throws RecognitionException {
        try {
            return super.recoverInline(recognizer);
        } catch (ParseCancellationException parseException) {
            throw mkException(recognizer, null);
        }
    }

    private InvalidSyntaxError mkException(Parser recognizer, RecognitionException cause) {
        int line = -1;
        int column = -1;
        String msg = null;
        String sourcePreview = null;
        Token currentToken = recognizer.getCurrentToken();
        if (currentToken != null) {
            line = currentToken.getLine();
            column = currentToken.getCharPositionInLine();
            sourcePreview = mkPreview(line);
            msg = "Offending token is '" + currentToken.getText() + "'";
        }
        return new InvalidSyntaxError(singletonList(new SourceLocation(line, column, sourceName)), msg, sourcePreview, cause);
    }

    /* grabs 3 lines before and after the syntax error */
    private String mkPreview(int line) {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(input));
        int startLine = line - 3;
        int endLine = line + 3;
        try {
            List<String> lines = readAllLines(reader);
            for (int i = 0; i < lines.size(); i++) {
                if (i >= startLine && i <= endLine) {
                    sb.append(lines.get(i)).append('\n');
                }
            }
        } catch (IOException ignored) {
            // this cant happen - its in memory
        }
        return sb.toString();
    }

    private List<String> readAllLines(BufferedReader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        String ln;
        while ((ln = reader.readLine()) != null) {
            lines.add(ln);
        }
        reader.close();
        return lines;
    }
}
