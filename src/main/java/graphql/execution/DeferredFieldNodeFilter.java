package graphql.execution;

import graphql.language.Directive;

import java.util.List;
import java.util.Map;

public class DeferredFieldNodeFilter implements FieldNodeFilter {
    private final ConditionalNodes conditionalNodes;

    public DeferredFieldNodeFilter() {
        conditionalNodes = new ConditionalNodes();
    }

    public boolean includeNode(Map<String, Object> variables, List<Directive> directives) {
        return conditionalNodes.shouldInclude(variables, directives) && DeferredNodes.deferedNode(directives);
    }

}
