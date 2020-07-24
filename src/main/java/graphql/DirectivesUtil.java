package graphql;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.util.FpKit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Internal
public class DirectivesUtil {

    public static Map<String, List<GraphQLDirective>> directivesByName(List<GraphQLDirective> directiveList) {
        return FpKit.groupingBy(directiveList, gd -> gd.getName());
    }

    public static Optional<GraphQLArgument> directiveWithArg(List<GraphQLDirective> directiveList, String directiveName, String argumentName) {
        GraphQLDirective graphQLDirective = FpKit.findOneOrNull(directiveList, d -> d.getName().equals(directiveName));

        GraphQLArgument argument = null;
        if (graphQLDirective != null) {
            argument = graphQLDirective.getArgument(argumentName);
        }
        return Optional.ofNullable(argument);
    }
}
