package graphql.parser;

import graphql.Internal;
import graphql.language.SourceLocation;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@Internal
public class AntlrHelper {

    public static SourceLocation createSourceLocation(MultiSourceReader multiSourceReader, int antrlLine, int charPositionInLine) {
        // multi source reader lines are 0 based while Antler lines are 1's based
        //
        // Antler columns ironically are 0 based - go figure!
        //
        int tokenLine = antrlLine - 1;
        MultiSourceReader.SourceAndLine sourceAndLine = multiSourceReader.getSourceAndLineFromOverallLine(tokenLine);
        //
        // graphql spec says line numbers and columns start at 1
        int line = sourceAndLine.getLine() + 1;
        int column = charPositionInLine + 1;
        return new SourceLocation(line, column, sourceAndLine.getSourceName());

    }

    public static SourceLocation createSourceLocation(MultiSourceReader multiSourceReader, Token token) {
        return AntlrHelper.createSourceLocation(multiSourceReader, token.getLine(), token.getCharPositionInLine());
    }

    public static SourceLocation createSourceLocation(MultiSourceReader multiSourceReader, TerminalNode terminalNode) {
        return AntlrHelper.createSourceLocation(multiSourceReader, terminalNode.getSymbol().getLine(), terminalNode.getSymbol().getCharPositionInLine());
    }

    /* grabs 3 lines before and after the syntax error */
    public static String createPreview(MultiSourceReader multiSourceReader, int antrlLine) {
        int line = antrlLine - 1;
        StringBuilder sb = new StringBuilder();
        int startLine = line - 3;
        int endLine = line + 3;
        List<String> lines = multiSourceReader.getData();
        for (int i = 0; i < lines.size(); i++) {
            if (i >= startLine && i <= endLine) {
                sb.append(lines.get(i)).append('\n');
            }
        }
        return sb.toString();

    }

}
