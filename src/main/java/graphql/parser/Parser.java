package graphql.parser;

import graphql.Internal;
import graphql.language.Document;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.List;

@Internal
public class Parser {

    public Document parseDocument(String input) {

        GraphqlLexer lexer = new GraphqlLexer(new ANTLRInputStream(input));

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GraphqlParser parser = new GraphqlParser(tokens);
        parser.removeErrorListeners();
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        parser.setErrorHandler(new BailErrorStrategy());
        GraphqlParser.DocumentContext document = parser.document();


        GraphqlAntlrToLanguage antlrToLanguage = new GraphqlAntlrToLanguage(tokens);
        antlrToLanguage.visitDocument(document);

        Token stop = document.getStop();
        List<Token> allTokens = tokens.getTokens();
        if (stop != null && allTokens != null && !allTokens.isEmpty()) {
            Token last = allTokens.get(allTokens.size() - 1);
            //
            // do we have more tokens in the stream than we consumed in the parse?
            // if yes then its invalid.  We make sure its the same channel
            boolean notEOF = last.getType() != Token.EOF;
            boolean lastGreaterThanDocument = last.getTokenIndex() > stop.getTokenIndex();
            boolean sameChannel = last.getChannel() == stop.getChannel();
            if (notEOF && lastGreaterThanDocument && sameChannel) {
                throw new ParseCancellationException("There are more tokens in the query that have not been consumed");
            }
        }
        return antlrToLanguage.result;
    }
}
