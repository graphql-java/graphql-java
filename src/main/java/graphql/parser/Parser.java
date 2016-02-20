package graphql.parser;

import graphql.language.Document;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Parser class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class Parser {

    private static final Logger log = LoggerFactory.getLogger(Parser.class);


    /**
     * <p>parseDocument.</p>
     *
     * @param input a {@link java.lang.String} object.
     * @return a {@link graphql.language.Document} object.
     */
    public Document parseDocument(String input) {

        GraphqlLexer lexer = new GraphqlLexer(new ANTLRInputStream(input));

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GraphqlParser parser = new GraphqlParser(tokens);
        parser.removeErrorListeners();
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        parser.setErrorHandler(new BailErrorStrategy());
        GraphqlParser.DocumentContext document = parser.document();


        GraphqlAntlrToLanguage antlrToLanguage = new GraphqlAntlrToLanguage();
        antlrToLanguage.visitDocument(document);
        return antlrToLanguage.result;
    }
}
