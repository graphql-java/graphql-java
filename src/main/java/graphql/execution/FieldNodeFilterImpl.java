package graphql.execution;

import graphql.language.Directive;

import java.util.List;
import java.util.Map;

public class FieldNodeFilterImpl implements FieldNodeFilter {
    private final ConditionalNodes conditionalNodes;

    public FieldNodeFilterImpl() {
        conditionalNodes = new ConditionalNodes();
    }

    @Override
    public boolean includeNode(Map<String, Object> variables, List<Directive> directives) {
        return conditionalNodes.shouldInclude(variables, directives);
    }

}
