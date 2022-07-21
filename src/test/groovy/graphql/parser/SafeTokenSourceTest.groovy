package graphql.parser

import graphql.parser.antlr.GraphqlLexer
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import spock.lang.Specification

import java.util.function.Consumer

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
        Consumer<Token> onToMany = { token ->
            offendingToken = token
            throw new IllegalStateException("stop!")
        }
        def tokenSource = new SafeTokenSource(graphqlLexer, 1000, onToMany)

        consumeAllTokens(tokenSource)
        assert false, "This is not meant to actually consume all tokens"

        then:
        thrown(IllegalStateException)
        offendingToken != null
        offendingToken.getChannel() == 3 // whitespace
        offendingToken.getText() == " "
    }

    def "can call back to the consumer when max graphql tokens are encountered"() {

        def offendingText = "@lol" * 1000
        GraphqlLexer graphqlLexer = lexer("""
                query foo { _typename $offendingText }
        """)
        when:
        Token offendingToken = null
        Consumer<Token> onToMany = { token ->
            offendingToken = token
            throw new IllegalStateException("stop!")
        }
        def tokenSource = new SafeTokenSource(graphqlLexer, 1000, onToMany)

        consumeAllTokens(tokenSource)
        assert false, "This is not meant to actually consume all tokens"

        then:
        thrown(IllegalStateException)
        offendingToken != null
        offendingToken.getChannel() == 0 // grammar
    }

    def "can safely get to the end of text if its ok"() {

        GraphqlLexer graphqlLexer = lexer("""
                query foo { _typename @lol@lol@lol }
        """)
        when:
        Token offendingToken = null
        Consumer<Token> onToMany = { token ->
            offendingToken = token
            throw new IllegalStateException("stop!")
        }
        def tokenSource = new SafeTokenSource(graphqlLexer, 1000, onToMany)

        consumeAllTokens(tokenSource)

        then:
        noExceptionThrown()
        offendingToken == null
    }

}
