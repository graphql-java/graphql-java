package graphql;

import graphql.schema.GraphQLDirective;
import graphql.util.FpKit;

import java.util.List;
import java.util.Map;

@Internal
public class DirectivesUtil {

    public static Map<String, GraphQLDirective> directivesByName(List<GraphQLDirective> directiveList) {
        return FpKit.getByName(directiveList, GraphQLDirective::getName, FpKit.mergeFirst());
    }
}
