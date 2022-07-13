package graphql.parser;

import graphql.Internal;
import graphql.language.Node;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.HashMap;
import java.util.Map;

/**
 * A parser that will capture parsing context data which can be later used for accessing tokens that are discarded
 * during the conventional parsing process (like comments).
 */
@Internal
public class NodeToRuleCapturingParser extends Parser {
    private final ParserContext parserContext;

    public NodeToRuleCapturingParser() {
        parserContext = new ParserContext();
    }

    @Override
    protected GraphqlAntlrToLanguage getAntlrToLanguage(CommonTokenStream tokens, MultiSourceReader multiSourceReader, ParserOptions parserOptions) {
        parserContext.tokens = tokens;
        return new GraphqlAntlrToLanguage(tokens, multiSourceReader, parserOptions, parserContext.nodeToRuleMap);
    }

    public ParserContext getParserContext() {
        return parserContext;
    }

    static public class ParserContext {
        private final Map<Node<?>, ParserRuleContext> nodeToRuleMap;
        private CommonTokenStream tokens;

        public ParserContext() {
            this.nodeToRuleMap = new HashMap<>();
        }

        protected CommonTokenStream getTokens() {
            return tokens;
        }

        protected Map<Node<?>, ParserRuleContext> getNodeToRuleMap() {
            return nodeToRuleMap;
        }
    }
}
