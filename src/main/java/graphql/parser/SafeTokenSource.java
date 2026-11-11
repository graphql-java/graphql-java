package graphql.parser;

import graphql.Internal;
import graphql.parser.antlr.GraphqlLexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Interval;

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

    private static final int MAX_NUMERIC_LITERAL_DIGITS = 1000;

    private final TokenSource lexer;
    private final int maxTokens;
    private final int maxWhitespaceTokens;
    private final int maxNumericLiteralCharacters;
    private final BiConsumer<Integer, Token> whenMaxTokensExceeded;
    private final BiConsumer<Integer, Token> whenMaxNumericLiteralCharactersExceeded;
    private final int channelCounts[];

    public SafeTokenSource(TokenSource lexer, int maxTokens, int maxWhitespaceTokens, int maxNumericLiteralCharacters,
                           BiConsumer<Integer, Token> whenMaxTokensExceeded,
                           BiConsumer<Integer, Token> whenMaxNumericLiteralCharactersExceeded) {
        this.lexer = lexer;
        this.maxTokens = maxTokens;
        this.maxWhitespaceTokens = maxWhitespaceTokens;
        this.maxNumericLiteralCharacters = maxNumericLiteralCharacters;
        this.whenMaxTokensExceeded = whenMaxTokensExceeded;
        this.whenMaxNumericLiteralCharactersExceeded = whenMaxNumericLiteralCharactersExceeded;
        // this could be a Map<int,int> however we want it to be faster as possible.
        // we only have 3 channels - but they are 0,2 and 3 so use 5 for safety - still faster than a map get/put
        // if we ever add another channel beyond 5 it will IOBEx during tests so future changes will be handled before release!
        this.channelCounts = new int[]{0, 0, 0, 0, 0};
    }


    @Override
    public Token nextToken() {
        callbackIfNumericInputTooLong();
        Token token = lexer.nextToken();
        if (token != null) {
            callbackIfNumericLiteralTooLong(token);
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

    private void callbackIfNumericInputTooLong() {
        // Inspect a bounded numeric prefix before ANTLR tries to match the whole token.
        CharStream input = lexer.getInputStream();
        int first = input.LA(1);
        if (!isDigit(first) && first != '-') {
            return;
        }

        int offset = first == '-' ? 2 : 1;
        if (!isDigit(input.LA(offset))) {
            return;
        }

        int maxDigits = Math.min(maxNumericLiteralCharacters, MAX_NUMERIC_LITERAL_DIGITS);
        int digits = 0;
        boolean hasFraction = false;
        boolean hasExponent = false;
        while (true) {
            int next = input.LA(offset);
            if (isDigit(next)) {
                if (++digits > maxDigits) {
                    reportOversizedNumericInput(input, offset, hasFraction || hasExponent, maxDigits);
                    return;
                }
                offset++;
                continue;
            }
            if (next == '.' && !hasFraction && !hasExponent && isDigit(input.LA(offset + 1))) {
                hasFraction = true;
                offset++;
                continue;
            }
            if ((next == 'e' || next == 'E') && !hasExponent) {
                int prefixLength = exponentPrefixLength(input, offset);
                if (prefixLength > 0) {
                    hasExponent = true;
                    offset += prefixLength;
                    continue;
                }
            }
            return;
        }
    }

    private int exponentPrefixLength(CharStream input, int offset) {
        int next = input.LA(offset + 1);
        if (isDigit(next)) {
            return 1;
        }
        if ((next == '+' || next == '-') && isDigit(input.LA(offset + 2))) {
            return 2;
        }
        return 0;
    }

    private boolean isDigit(int character) {
        return character >= '0' && character <= '9';
    }

    private void reportOversizedNumericInput(CharStream input, int offset, boolean isFloat, int maxDigits) {
        int startIndex = input.index();
        CommonToken token = new CommonToken(isFloat ? GraphqlLexer.FloatValue : GraphqlLexer.IntValue);
        token.setLine(lexer.getLine());
        token.setCharPositionInLine(lexer.getCharPositionInLine());
        token.setStartIndex(startIndex);
        token.setStopIndex(startIndex + offset - 1);
        token.setText(input.getText(Interval.of(startIndex, startIndex + offset - 1)));
        whenMaxNumericLiteralCharactersExceeded.accept(maxDigits, token);
    }

    private void callbackIfNumericLiteralTooLong(Token token) {
        int tokenType = token.getType();
        if (tokenType != GraphqlLexer.IntValue && tokenType != GraphqlLexer.FloatValue) {
            return;
        }

        int characterCount = token.getStopIndex() - token.getStartIndex() + 1;
        if (characterCount > maxNumericLiteralCharacters) {
            whenMaxNumericLiteralCharactersExceeded.accept(maxNumericLiteralCharacters, token);
        }
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
