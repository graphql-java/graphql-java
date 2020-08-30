package graphql;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.util.FpKit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Internal
public class DirectivesUtil {


    public static Map<String, GraphQLDirective> nonRepeatableDirectivesByName(List<GraphQLDirective> directives) {
        Map<String, List<GraphQLDirective>> map = allDirectivesByName(directives);
        List<GraphQLDirective> singletonDirectives = map.entrySet().stream()
                // only those that have 1 non repeated entry
                .filter(e -> e.getKey() != null && isAllNonRepeatable(e.getValue()))
                .flatMap(e -> e.getValue().stream()).collect(Collectors.toList());
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
        Assert.assertTrue(isAllNonRepeatable(directiveList), () -> String.format("%s is a repeatable directive", directiveName));
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

    private static boolean isAllNonRepeatable(List<GraphQLDirective> directives) {
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
}
