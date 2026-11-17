package graphql.parser;

import graphql.Internal;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.jspecify.annotations.NullMarked;

@Internal
@NullMarked
public class GraphqlLexerATNSimulator extends LexerATNSimulator {

    public GraphqlLexerATNSimulator(Lexer lexer) {
        super(lexer, lexer.getATN(), lexer.getInterpreter().decisionToDFA, lexer.getInterpreter().getSharedContextCache());
    }

    @Override
    public void consume(CharStream input) {
        int codePoint = input.LA(1);
        // ANTLR only counts LF. GraphQL also counts a standalone CR, while CRLF counts once.
        if (codePoint == '\n' || (codePoint == '\r' && input.LA(2) != '\n')) {
            setLine(getLine() + 1);
            setCharPositionInLine(0);
        } else {
            setCharPositionInLine(getCharPositionInLine() + 1);
        }
        input.consume();
    }
}
