package graphql.parser

import graphql.parser.antlr.GraphqlLexer
import graphql.parser.antlr.NumericLiteralTooLongException
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import spock.lang.Specification

import java.util.function.BiConsumer

class SafeTokenSourceTest extends Specification {

    private void consumeAllTokens(SafeTokenSource tokenSource) {
        def nextToken = tokenSource.nextToken()
        while (nextToken != null && nextToken.getType() != Token.EOF) {
            nextToken = tokenSource.nextToken()
        }
    }

    private GraphqlLexer lexer(doc) {
        def charStream = CharStreams.fromString(doc)
        def graphqlLexer = new GraphqlLexer(charStream)
        graphqlLexer
    }

    def "can call back to the consumer when max whitespace tokens are encountered"() {

        def offendingText = " " * 1000
        GraphqlLexer graphqlLexer = lexer("""
                query foo { _typename $offendingText @lol@lol@lol }
        """)
        when:
        Token offendingToken = null
        BiConsumer<Integer, Token> onTooManyTokens = { max, token ->
            offendingToken = token
            throw new IllegalStateException("stop at $max")
        }
        def tokenSource = new SafeTokenSource(graphqlLexer, 50, 1000, Integer.MAX_VALUE, onTooManyTokens, { max, token -> })

        consumeAllTokens(tokenSource)
        assert false, "This is not meant to actually consume all tokens"

        then:
        def e = thrown(IllegalStateException)
        e.message == "stop at 1000"
        offendingToken != null
        offendingToken.getChannel() == 3 // whitespace
        offendingToken.getText() == " "
    }

    def "can call back to the consumer when max grammar tokens are encountered"() {

        def offendingText = "@lol" * 1000
        GraphqlLexer graphqlLexer = lexer("""
                query foo { _typename $offendingText }
        """)
        when:
        Token offendingToken = null
        BiConsumer<Integer, Token> onTooManyTokens = { max, token ->
            offendingToken = token
            throw new IllegalStateException("stop at $max")
        }
        def tokenSource = new SafeTokenSource(graphqlLexer, 1000, 200_000, Integer.MAX_VALUE, onTooManyTokens, { max, token -> })

        consumeAllTokens(tokenSource)
        assert false, "This is not meant to actually consume all tokens"

        then:
        def e = thrown(IllegalStateException)
        e.message == "stop at 1000"
        offendingToken != null
        offendingToken.getChannel() == 0 // grammar
    }

    def "can safely get to the end of text if its ok"() {

        GraphqlLexer graphqlLexer = lexer("""
                query foo { _typename @lol@lol@lol }
        """)
        when:
        Token offendingToken = null
        BiConsumer<Integer, Token> onTooManyTokens = { max, token ->
            offendingToken = token
            throw new IllegalStateException("stop at $max")
        }
        def tokenSource = new SafeTokenSource(graphqlLexer, 1000, 200_000, Integer.MAX_VALUE, onTooManyTokens, { max, token -> })

        consumeAllTokens(tokenSource)

        then:
        noExceptionThrown()
        offendingToken == null
    }

    def "can call back before returning an oversized numeric literal"() {
        given:
        GraphqlLexer graphqlLexer = lexer("query { field(arg: ${"9" * 101}) }")
        Token offendingToken = null
        BiConsumer<Integer, Token> onTooManyNumericLiteralCharacters = { max, token ->
            offendingToken = token
            throw new IllegalStateException("stop at $max")
        }
        def tokenSource = new SafeTokenSource(
                graphqlLexer,
                1000,
                200_000,
                100,
                { max, token -> },
                onTooManyNumericLiteralCharacters
        )

        when:
        consumeAllTokens(tokenSource)

        then:
        def exception = thrown(IllegalStateException)
        exception.message == "stop at 100"
        offendingToken.type == Token.INVALID_TYPE
        offendingToken.stopIndex - offendingToken.startIndex + 1 == 101
    }

    def "oversized numeric literal still stops lexing when callback returns"() {
        given:
        GraphqlLexer graphqlLexer = lexer("9" * 101)
        Token offendingToken = null
        def tokenSource = new SafeTokenSource(
                graphqlLexer,
                1000,
                200_000,
                100,
                { max, token -> },
                { max, token -> offendingToken = token }
        )

        when:
        tokenSource.nextToken()

        then:
        def exception = thrown(NumericLiteralTooLongException)
        exception.maxCharacters == 100
        offendingToken.text == "9" * 101
    }

    def "can stop numeric input while the lexer matches it"() {
        given:
        GraphqlLexer graphqlLexer = lexer("${numericInput}")
        Token offendingToken = null
        def tokenSource = new SafeTokenSource(
                graphqlLexer,
                1000,
                200_000,
                configuredLimit,
                { max, token -> },
                { max, token ->
                    offendingToken = token
                    throw new IllegalStateException("stop at $max")
                }
        )

        when:
        tokenSource.nextToken()

        then:
        def exception = thrown(IllegalStateException)
        exception.message == "stop at $expectedLimit"
        graphqlLexer.inputStream.index() <= offendingToken.stopIndex + 1
        offendingToken.type == Token.INVALID_TYPE
        offendingToken.text == numericInput.substring(0, offendingToken.stopIndex + 1)

        where:
        numericInput                                      | configuredLimit | expectedLimit
        "9" * 101                                        | 100             | 100
        "9" * 2001                                       | 2000            | 2000
        "-" + "9" * 2000                                 | 2000            | 2000
        "9" * 800 + "." + "9" * 800 + "e+" + "9" * 398 | 2000            | 2000
        "1e" + "9" * 1999 + "x"                          | 2000            | 2000
        "9" * 10_000                                      | 2000            | 2000
        "-1"                                             | 1               | 1
        "0"                                              | 0               | 0
        "9"                                              | 0               | 0
        "0.1"                                            | 2               | 2
        "1e+2"                                           | 3               | 3
    }

    def "numeric literals at the configured character limit are accepted"() {
        given:
        GraphqlLexer graphqlLexer = lexer(numericInput)
        def tokenSource = new SafeTokenSource(
                graphqlLexer,
                1000,
                200_000,
                configuredLimit,
                { max, token -> },
                { max, token -> throw new IllegalStateException("unexpected numeric limit") }
        )

        when:
        consumeAllTokens(tokenSource)

        then:
        noExceptionThrown()

        where:
        numericInput | configuredLimit
        "0"          | 1
        "9"          | 1
        "-0"         | 2
        "-1"         | 2
        "1e+2"       | 4
    }

    def "character limit is applied to each numeric token separately"() {
        given:
        GraphqlLexer graphqlLexer = lexer("${"9" * 600}-${"9" * 599}")
        def tokenSource = new SafeTokenSource(
                graphqlLexer,
                1000,
                200_000,
                600,
                { max, token -> },
                { max, token -> throw new IllegalStateException("unexpected numeric limit") }
        )

        when:
        consumeAllTokens(tokenSource)

        then:
        noExceptionThrown()
    }

    def "numeric punctuation fits exactly at the character limit"() {
        given:
        GraphqlLexer graphqlLexer = lexer("${"9" * 500}.${"9" * 500}")
        def tokenSource = new SafeTokenSource(
                graphqlLexer,
                1000,
                200_000,
                1001,
                { max, token -> },
                { max, token -> throw new IllegalStateException("unexpected numeric limit") }
        )

        when:
        consumeAllTokens(tokenSource)

        then:
        noExceptionThrown()
    }

}
