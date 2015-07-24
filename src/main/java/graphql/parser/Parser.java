package graphql.parser;

import graphql.language.Document;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.atn.PredictionMode;

public class Parser {


    public Document parseDocument(String input) {

        GraphqlLexer lexer = new GraphqlLexer(new ANTLRInputStream(input));

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GraphqlParser parser = new GraphqlParser(tokens);
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

}
