package graphql;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.util.FpKit;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;

@Internal
public class DirectivesUtil {


    public static Map<String, GraphQLDirective> nonRepeatableDirectivesByName(List<GraphQLDirective> directives) {
        // filter the repeatable directives
        List<GraphQLDirective> singletonDirectives = directives.stream()
                .filter(d -> !d.isRepeatable()).collect(Collectors.toList());

        return FpKit.getByName(singletonDirectives, GraphQLDirective::getName);
    }

    public static Map<String, ImmutableList<GraphQLDirective>> allDirectivesByName(List<GraphQLDirective> directives) {

        return ImmutableMap.copyOf(FpKit.groupingBy(directives, GraphQLDirective::getName));
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

    public static List<GraphQLDirective> add(List<GraphQLDirective> targetList, GraphQLDirective newDirective) {
        assertNotNull(targetList, () -> "directive list can't be null");
        assertNotNull(newDirective, () -> "directive can't be null");
        targetList.add(newDirective);
        return targetList;
    }

    public static List<GraphQLDirective> addAll(List<GraphQLDirective> targetList, List<GraphQLDirective> newDirectives) {
        assertNotNull(targetList, () -> "directive list can't be null");
        assertNotNull(newDirectives, () -> "directive list can't be null");
        targetList.addAll(newDirectives);
        return targetList;
    }

    public static GraphQLDirective getFirstDirective(String name, Map<String, List<GraphQLDirective>> allDirectivesByName) {
        List<GraphQLDirective> directives = allDirectivesByName.getOrDefault(name, emptyList());
        if (directives.isEmpty()) {
            return null;
        }
        return directives.get(0);
    }

    /**
     * A holder class that breaks a list of directives into maps to be more easily accessible in using classes
     */
    public static class DirectivesHolder {

        private final ImmutableMap<String, List<GraphQLDirective>> allDirectivesByName;
        private final ImmutableMap<String, GraphQLDirective> nonRepeatableDirectivesByName;
        private final List<GraphQLDirective> allDirectives;

        public DirectivesHolder(Collection<GraphQLDirective> allDirectives) {
            this.allDirectives = ImmutableList.copyOf(allDirectives);
            this.allDirectivesByName = ImmutableMap.copyOf(FpKit.groupingBy(allDirectives, GraphQLDirective::getName));
            // filter out the repeatable directives
            List<GraphQLDirective> nonRepeatableDirectives = allDirectives.stream()
                    .filter(d -> !d.isRepeatable()).collect(Collectors.toList());
            this.nonRepeatableDirectivesByName = ImmutableMap.copyOf(FpKit.getByName(nonRepeatableDirectives, GraphQLDirective::getName));
        }

        public ImmutableMap<String, List<GraphQLDirective>> getAllDirectivesByName() {
            return allDirectivesByName;
        }

        public ImmutableMap<String, GraphQLDirective> getDirectivesByName() {
            return nonRepeatableDirectivesByName;
        }

        public List<GraphQLDirective> getDirectives() {
            return allDirectives;
        }

        public GraphQLDirective getDirective(String directiveName) {
            List<GraphQLDirective> directiveList = allDirectivesByName.get(directiveName);
            if (directiveList == null || directiveList.isEmpty()) {
                return null;
            }
            return directiveList.get(0);

        }

        public List<GraphQLDirective> getDirectives(String directiveName) {
            return allDirectivesByName.getOrDefault(directiveName, emptyList());
        }
    }
}
