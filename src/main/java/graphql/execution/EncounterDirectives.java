package graphql.execution;

import graphql.Internal;
import graphql.schema.GraphQLDirective;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class EncounterDirectives {

    private Map<String, List<GraphQLDirective>> fieldDirectives;
    private Map<String, List<GraphQLDirective>> fragmentSpreadDirectives;
    private Map<String, List<GraphQLDirective>> fragmentDefinitionDirectives;
    private Map<String, List<GraphQLDirective>> inlineFragmentDirectives;
    private Map<String, GraphQLDirective> operationDirectives;

    @Internal
    public EncounterDirectives(Map<String, List<GraphQLDirective>> fieldDirectives,
                               Map<String, List<GraphQLDirective>> fragmentSpreadDirectives,
                               Map<String, List<GraphQLDirective>> fragmentDefinitionDirectives,
                               Map<String, List<GraphQLDirective>> inlineFragmentDirectives,
                               Map<String, GraphQLDirective> operationDirectives) {
        this.fieldDirectives = fieldDirectives;
        this.fragmentSpreadDirectives = fragmentSpreadDirectives;
        this.fragmentDefinitionDirectives = fragmentDefinitionDirectives;
        this.inlineFragmentDirectives = inlineFragmentDirectives;
        this.operationDirectives = operationDirectives;
    }

    public Map<String, List<GraphQLDirective>> getFieldDirectives() {
        return fieldDirectives;
    }

    public Map<String, List<GraphQLDirective>> getFragmentSpreadDirectives() {
        return fragmentSpreadDirectives;
    }

    public Map<String, List<GraphQLDirective>> getFragmentDefinitionDirectives() {
        return fragmentDefinitionDirectives;
    }

    public Map<String, List<GraphQLDirective>> getInlineFragmentDirectives() {
        return inlineFragmentDirectives;
    }

    public Map<String, GraphQLDirective> getOperationDirectives() {
        return operationDirectives;
    }

    public GraphQLDirective getFieldDirective(String directiveName) {
        return getDirective(fieldDirectives, directiveName);
    }

    public GraphQLDirective getFragmentSpreadDirective(String directiveName) {
        return getDirective(fragmentSpreadDirectives, directiveName);
    }

    public GraphQLDirective getFragmentDefinitionDirective(String directiveName) {
        return getDirective(fragmentDefinitionDirectives, directiveName);
    }

    public GraphQLDirective getInlineFragmentDirective(String directiveName) {
        return getDirective(inlineFragmentDirectives, directiveName);
    }

    public GraphQLDirective getOperationDirective(String directiveName) {
        return operationDirectives.get(directiveName);
    }

    public GraphQLDirective getFirstApplicableDirective(String directiveName) {
        return Stream.of(
                getFieldDirective(directiveName),
                getInlineFragmentDirective(directiveName),
                getFragmentSpreadDirective(directiveName),
                getFragmentDefinitionDirective(directiveName))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private GraphQLDirective getDirective(Map<String, List<GraphQLDirective>> directiveMap, String directiveName) {
        List<GraphQLDirective> directives = directiveMap.get(directiveName);
        if (directives == null || directiveName.isEmpty()) {
            return null;
        }
        return directives.get(0);
    }
}
