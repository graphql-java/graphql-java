package graphql.parser;

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

    InvalidSyntaxException mkMoreTokensException(Token token) {
        SourceLocation sourceLocation = new SourceLocation(token.getLine(), token.getCharPositionInLine());
        String sourcePreview = mkPreview(token.getLine());
        return new InvalidSyntaxException(sourceLocation,
                "There are more tokens in the query that have not been consumed",
                sourcePreview, token.getText(), null);
    }


    private InvalidSyntaxException mkException(Parser recognizer, RecognitionException cause) {
        String sourcePreview = null;
        String offendingToken = null;
        SourceLocation sourceLocation = null;
        Token currentToken = recognizer.getCurrentToken();
        if (currentToken != null) {
            int line = currentToken.getLine();
            int column = currentToken.getCharPositionInLine();
            offendingToken = currentToken.getText();
            sourcePreview = mkPreview(line);
            sourceLocation = new SourceLocation(line, column, sourceName);
        }
        return new InvalidSyntaxException(sourceLocation, null, sourcePreview, offendingToken, cause);
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
