package graphql.parser;

import graphql.Internal;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;

import java.util.function.BiConsumer;

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
    private final int maxWhitespaceTokens;
    private final BiConsumer<Integer, Token> whenMaxTokensExceeded;
    private final int channelCounts[];

    public SafeTokenSource(TokenSource lexer, int maxTokens, int maxWhitespaceTokens, BiConsumer<Integer, Token> whenMaxTokensExceeded) {
        this.lexer = lexer;
        this.maxTokens = maxTokens;
        this.maxWhitespaceTokens = maxWhitespaceTokens;
        this.whenMaxTokensExceeded = whenMaxTokensExceeded;
        // this could be a Map<int,int> however we want it to be faster as possible.
        // we only have 3 channels - but they are 0,2 and 3 so use 5 for safety - still faster than a map get/put
        // if we ever add another channel beyond 5 it will IOBEx during tests so future changes will be handled before release!
        this.channelCounts = new int[]{0, 0, 0, 0, 0};
    }


    @Override
    public Token nextToken() {
        Token token = lexer.nextToken();
        if (token != null) {
            int channel = token.getChannel();
            int currentCount = ++channelCounts[channel];
            if (channel == Parser.CHANNEL_WHITESPACE) {
                // whitespace gets its own max count
                callbackIfMaxExceeded(maxWhitespaceTokens, currentCount, token);
            } else {
                callbackIfMaxExceeded(maxTokens, currentCount, token);
            }
        }
        return token;
    }

    private void callbackIfMaxExceeded(int maxCount, int currentCount, Token token) {
        if (currentCount > maxCount) {
            whenMaxTokensExceeded.accept(maxCount, token);
        }
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
