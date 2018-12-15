package graphql.parser;

import graphql.Internal;
import graphql.InvalidSyntaxError;
import graphql.language.Document;
import graphql.language.SourceLocation;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;

import java.util.List;

@Internal
public class Parser {

    public Document parseDocument(String input) throws InvalidSyntaxError {
        return parseDocument(input, null);
    }

    public Document parseDocument(String input, String sourceName) throws InvalidSyntaxError {

        CharStream charStream;
        if (sourceName == null) {
            charStream = CharStreams.fromString(input);
        } else {
            charStream = CharStreams.fromString(input, sourceName);
        }

        GraphqlLexer lexer = new GraphqlLexer(charStream);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GraphqlParser parser = new GraphqlParser(tokens);
        parser.removeErrorListeners();
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        parser.setErrorHandler(new ExtendedBailStrategy(input, sourceName));
        GraphqlParser.DocumentContext documentContext = parser.document();


        GraphqlAntlrToLanguage antlrToLanguage = new GraphqlAntlrToLanguage(tokens);
        Document doc = antlrToLanguage.createDocument(documentContext);

        Token stop = documentContext.getStop();
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
                SourceLocation sourceLocation = new SourceLocation(last.getLine(), last.getCharPositionInLine());
                throw new InvalidSyntaxError(sourceLocation, "There are more tokens in the query that have not been consumed");
            }
        }
        return doc;
    }
}
