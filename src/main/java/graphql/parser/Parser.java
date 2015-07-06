package graphql.parser;

import graphql.language.Document;
import graphql.parser.antlr.GraphqlLexer;
import graphql.parser.antlr.GraphqlParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class Parser {

    public Document parseDocument(String input) {

        GraphqlLexer lexer = new GraphqlLexer(new ANTLRInputStream(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        GraphqlParser parser = new GraphqlParser(tokens);
        GraphqlParser.DocumentContext document = parser.document();

        ParseTreeWalker walker = new ParseTreeWalker();
        GraphqlAntlrToLanguage treeListener = new GraphqlAntlrToLanguage();
        walker.walk(treeListener, document);
        return treeListener.result;
    }
}
