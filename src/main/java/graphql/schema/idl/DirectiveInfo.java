package graphql.schema.idl;

import graphql.Directives;
import graphql.PublicApi;
import graphql.schema.GraphQLDirective;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Info on all the directives provided by graphql specification
 */
@PublicApi
public class DirectiveInfo {

    /**
     * A set of directives which provided by graphql specification
     */
    public static final Set<GraphQLDirective> GRAPHQL_SPECIFICATION_DIRECTIVES = new LinkedHashSet<>();

    /**
     * A map from directive name to directive which provided by specification
     */
    public static final Map<String, GraphQLDirective> GRAPHQL_SPECIFICATION_DIRECTIVE_MAP = new LinkedHashMap<>();

    static {
        GRAPHQL_SPECIFICATION_DIRECTIVES.add(Directives.IncludeDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVES.add(Directives.SkipDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVES.add(Directives.DeprecatedDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVES.add(Directives.SpecifiedByDirective);
    }

    static {
        GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.put(Directives.IncludeDirective.getName(), Directives.IncludeDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.put(Directives.SkipDirective.getName(), Directives.SkipDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.put(Directives.DeprecatedDirective.getName(), Directives.DeprecatedDirective);
        GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.put(Directives.SpecifiedByDirective.getName(), Directives.SpecifiedByDirective);
    }


    /**
     * Returns true if a directive with provided directiveName has been defined in graphql specification
     *
     * @param directiveName the name of directive in question
     *
     * @return true if the directive provided by graphql specification, and false otherwise
     */
    public static boolean isGraphqlSpecifiedDirective(String directiveName) {
        return GRAPHQL_SPECIFICATION_DIRECTIVE_MAP.containsKey(directiveName);
    }

    /**
     * Returns true if the provided directive has been defined in graphql specification
     *
     * @param graphQLDirective the directive in question
     *
     * @return true if the directive provided by graphql specification, and false otherwise
     */
    public static boolean isGraphqlSpecifiedDirective(GraphQLDirective graphQLDirective) {
        return isGraphqlSpecifiedDirective(graphQLDirective.getName());
    }


}
