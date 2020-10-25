package graphql;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.util.FpKit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.emptyList;

@Internal
public class DirectivesUtil {


    public static Map<String, GraphQLDirective> nonRepeatableDirectivesByName(List<GraphQLDirective> directives) {
        // filter the repeatable directives
        List<GraphQLDirective> singletonDirectives = directives.stream()
                .filter(d -> !d.isRepeatable()).collect(Collectors.toList());

        return FpKit.getByName(singletonDirectives, GraphQLDirective::getName);
    }

    public static Map<String, List<GraphQLDirective>> allDirectivesByName(List<GraphQLDirective> directives) {
        return FpKit.groupingBy(directives, GraphQLDirective::getName);
    }

    public static GraphQLDirective nonRepeatedDirectiveByNameWithAssert(Map<String, List<GraphQLDirective>> directives, String directiveName) {
        List<GraphQLDirective> directiveList = directives.get(directiveName);
        if (directiveList == null || directiveList.isEmpty()) {
            return null;
        }
        Assert.assertTrue(isAllNonRepeatable(directiveList), () -> String.format("'%s' is a repeatable directive and you have used a non repeatable access method", directiveName));
        return directiveList.get(0);
    }

    public static Optional<GraphQLArgument> directiveWithArg(List<GraphQLDirective> directives, String directiveName, String argumentName) {
        GraphQLDirective directive = nonRepeatableDirectivesByName(directives).get(directiveName);
        GraphQLArgument argument = null;
        if (directive != null) {
            argument = directive.getArgument(argumentName);
        }
        return Optional.ofNullable(argument);
    }



    public static boolean isAllNonRepeatable(List<GraphQLDirective> directives) {
        if (directives == null || directives.isEmpty()) {
            return false;
        }
        for (GraphQLDirective graphQLDirective : directives) {
            if (graphQLDirective.isRepeatable()) {
                return false;
            }
        }
        return true;
    }

    public static List<GraphQLDirective> enforceAdd(List<GraphQLDirective> targetList, GraphQLDirective newDirective) {
        assertNotNull(targetList, () -> "directive list can't be null");
        assertNotNull(newDirective, () -> "directive can't be null");

        // check whether the newDirective is repeatable in advance, to avoid needless operations
        if(newDirective.isNonRepeatable()){
            Map<String, List<GraphQLDirective>> map = allDirectivesByName(targetList);
            assertNonRepeatable(newDirective, map);
        }
        targetList.add(newDirective);
        return targetList;
    }

    public static List<GraphQLDirective> enforceAddAll(List<GraphQLDirective> targetList, List<GraphQLDirective> newDirectives) {
        assertNotNull(targetList, () -> "directive list can't be null");
        assertNotNull(newDirectives, () -> "directive list can't be null");
        Map<String, List<GraphQLDirective>> map = allDirectivesByName(targetList);
        for (GraphQLDirective newDirective : newDirectives) {
            assertNonRepeatable(newDirective, map);
            targetList.add(newDirective);
        }
        return targetList;
    }

    private static void assertNonRepeatable(GraphQLDirective directive, Map<String, List<GraphQLDirective>> mapOfDirectives) {
        if (directive.isNonRepeatable()) {
            List<GraphQLDirective> currentDirectives = mapOfDirectives.getOrDefault(directive.getName(), emptyList());
            int currentSize = currentDirectives.size();
            if (currentSize > 0) {
                Assert.assertShouldNeverHappen("%s is a non repeatable directive but there is already one present in this list", directive.getName());
            }
        }
    }

    public static GraphQLDirective getFirstDirective(String name, Map<String, List<GraphQLDirective>> allDirectivesByName) {
        List<GraphQLDirective> directives = allDirectivesByName.getOrDefault(name, emptyList());
        if (directives.isEmpty()) {
            return null;
        }
        return directives.get(0);
    }
}
