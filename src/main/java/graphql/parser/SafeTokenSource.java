package graphql.parser;

import graphql.Internal;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This token source can wrap a lexer and if it asks for more than a maximum number of tokens
 * the user can take some action, typically throw an exception to stop lexing.
 *
 * It tracks the maximum number per token channel, so we have 3 at the moment, and they will all be tracked.
 *
 * This is used to protect us from evil input.  The lexer will eagerly try to find all tokens
 * at times and certain inputs (directives butted together for example) will cause the lexer
 * to keep doing work even though before the tokens are presented back to the parser
 * and hence before it has a chance to stop work once too much as been done.
 */
@Internal
public class SafeTokenSource implements TokenSource {
    private final TokenSource lexer;
    private final int maxTokens;
    private final Consumer<Token> whenMaxTokensExceeded;
    private final Map<Integer, Integer> channelCounts;

    public SafeTokenSource(TokenSource lexer, int maxTokens, Consumer<Token> whenMaxTokensExceeded) {
        this.lexer = lexer;
        this.maxTokens = maxTokens;
        this.whenMaxTokensExceeded = whenMaxTokensExceeded;
        this.channelCounts = new HashMap<>();
    }

    @Override
    public Token nextToken() {
        Token token = lexer.nextToken();
        if (token != null) {
            int channel = token.getChannel();
            Integer currentCount = channelCounts.getOrDefault(channel, 0);
            currentCount = currentCount + 1;
            if (currentCount > maxTokens) {
                whenMaxTokensExceeded.accept(token);
            }
            channelCounts.put(channel, currentCount);
        }
        return token;
    }

    @Override
    public int getLine() {
        return lexer.getLine();
    }

    @Override
    public int getCharPositionInLine() {
        return lexer.getCharPositionInLine();
    }

    @Override
    public CharStream getInputStream() {
        return lexer.getInputStream();
    }

    @Override
    public String getSourceName() {
        return lexer.getSourceName();
    }

    @Override
    public void setTokenFactory(TokenFactory<?> factory) {
        lexer.setTokenFactory(factory);
    }

    @Override
    public TokenFactory<?> getTokenFactory() {
        return lexer.getTokenFactory();
    }
}
