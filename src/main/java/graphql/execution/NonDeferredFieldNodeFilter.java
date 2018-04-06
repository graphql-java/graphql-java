package graphql.execution;

import graphql.language.Directive;

import java.util.List;
import java.util.Map;

public class NonDeferredFieldNodeFilter implements FieldNodeFilter {
    private final ConditionalNodes conditionalNodes;

    public NonDeferredFieldNodeFilter() {
        conditionalNodes = new ConditionalNodes();
    }

    public boolean includeNode(Map<String, Object> variables, List<Directive> directives) {
        return conditionalNodes.shouldInclude(variables, directives) && !DeferredNodes.deferedNode(directives);
    }

}
