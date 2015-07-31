package graphql.parser;

import graphql.language.Document;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Parser {

    private static final Logger log = LoggerFactory.getLogger(Parser.class);


    public Document parseDocument(String input) {

        GraphqlLexer lexer = new GraphqlLexer(new ANTLRInputStream(input));

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GraphqlParser parser = new GraphqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ErrorListener());
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        parser.setErrorHandler(new ErrorStrategy());
        GraphqlParser.DocumentContext document = parser.document();


        GraphqlAntlrToLanguage antlrToLanguage = new GraphqlAntlrToLanguage();
        antlrToLanguage.visitDocument(document);
        return antlrToLanguage.result;
    }

    private class ErrorStrategy extends DefaultErrorStrategy {

        @Override
        public void recover(org.antlr.v4.runtime.Parser recognizer, RecognitionException e) {
            throw e;
        }
    }

    private class ErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            log.error("graphql syntax error: line " + line + ":" + charPositionInLine + " " + msg);
        }
    }

}
