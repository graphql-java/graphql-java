package graphql.parser

import graphql.parser.antlr.GraphqlLexer
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
        offendingToken.type == GraphqlLexer.IntValue
        offendingToken.stopIndex - offendingToken.startIndex + 1 == 101
    }

}
