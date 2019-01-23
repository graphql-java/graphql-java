package graphql.execution.directives;

import graphql.Internal;
import graphql.schema.GraphQLDirective;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * These objects are ALWAYS in the context of a single MergedField
 */
@Internal
public class QueryDirectivesImpl implements QueryDirectives {

    private final List<QueryDirectivesInfo> directivePositions;

    public QueryDirectivesImpl() {
        this(Arrays.asList());
    }

    public QueryDirectivesImpl(List<QueryDirectivesInfo> directivesInfos) {
        this.directivePositions = assertNotNull(directivesInfos);
        Collections.sort(directivesInfos);
    }

    private Map<String, List<GraphQLDirective>> toMap(Stream<QueryDirectivesInfo> directivePositions) {
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

    public List<QueryDirectivesInfo> onlyNamed(String directiveName) {
        return directivePositions.stream()
                .map(info -> info.restrictTo(directiveName))
                .filter(info -> !info.getDirectives().isEmpty())
                .collect(toList());
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

    @Override
    public List<GraphQLDirective> getClosestDirective(String directiveName) {
        List<QueryDirectivesInfo> onlyNamed = onlyNamed(directiveName);

        Optional<Integer> minDistance = onlyNamed.stream().map(QueryDirectivesInfo::getDistance).min(Integer::compareTo);
        if (minDistance.isPresent()) {
            Map<String, List<GraphQLDirective>> min = toMap(onlyNamed.stream()
                    .filter(info -> info.getDistance() == minDistance.get()));
            return min.get(directiveName);
        } else {
            return emptyList();
        }
    }

    @Override
    public List<QueryDirectivesInfo> getAllDirectives() {
        return new ArrayList<>(directivePositions);
    }

    @Override
    public List<QueryDirectivesInfo> getAllDirectivesNamed(String directiveName) {
        return onlyNamed(directiveName);
    }
}
