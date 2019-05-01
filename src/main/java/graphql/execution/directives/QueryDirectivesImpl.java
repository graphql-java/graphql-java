package graphql.execution.directives;

import graphql.Internal;
import graphql.schema.GraphQLDirective;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.emptyList;

/**
 * These objects are ALWAYS in the context of a single MergedField
 */
@Internal
public class QueryDirectivesImpl implements QueryDirectives {

    private final List<AstNodeDirectives> directivePositions;

    public QueryDirectivesImpl() {
        this(Collections.emptyList());
    }

    public QueryDirectivesImpl(List<AstNodeDirectives> directivesInfos) {
        this.directivePositions = assertNotNull(directivesInfos);
        Collections.sort(directivesInfos);
    }

    private Map<String, List<GraphQLDirective>> toMap(Stream<AstNodeDirectives> directivePositions) {
        Map<String, List<GraphQLDirective>> mapOfDirectives = new LinkedHashMap<>();
        directivePositions.forEach(info -> {
            Map<String, GraphQLDirective> positionedDirectives = info.getDirectives();
            positionedDirectives.forEach((name, directive) -> {
                mapOfDirectives.computeIfAbsent(name, k -> new ArrayList<>());
                mapOfDirectives.get(name).add(directive);
            });
        });
        return mapOfDirectives;
    }

    @Override
    public Map<String, List<GraphQLDirective>> getImmediateDirectives() {
        return toMap(directivePositions.stream()
                .filter(info -> info.getDistance() == 0));
    }

    @Override
    public List<GraphQLDirective> getImmediateDirective(String directiveName) {
        return getImmediateDirectives().getOrDefault(directiveName, emptyList());
    }
}
