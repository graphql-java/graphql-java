package graphql;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.util.FpKit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Internal
public class DirectivesUtil {

    public static Map<String, GraphQLDirective> directivesByName(List<GraphQLDirective> directiveList) {
        return FpKit.getByName(directiveList, GraphQLDirective::getName, FpKit.mergeFirst());
    }

    public static Optional<GraphQLDirective> directiveByName(List<GraphQLDirective> directives, String directiveName) {
        for (GraphQLDirective directive : directives) {
            if (directive.getName().equals(directiveName)) {
                return Optional.of(directive);
            }
        }
        return Optional.empty();
    }

    public static Optional<GraphQLArgument> directiveWithArg(List<GraphQLDirective> directiveList, String directiveName, String argumentName) {
        GraphQLDirective directive = directiveByName(directiveList, directiveName).orElse(null);
        GraphQLArgument argument = null;
        if (directive != null) {
            argument = directive.getArgument(argumentName);
        }
        return Optional.ofNullable(argument);
    }
}
