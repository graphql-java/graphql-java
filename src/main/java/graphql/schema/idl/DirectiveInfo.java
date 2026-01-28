package graphql.schema.idl;

import graphql.Directives;
import graphql.PublicApi;
import graphql.schema.GraphQLDirective;

import java.util.Map;
import java.util.Set;

/**
 * Info on all the directives provided by graphql specification.
 *
 * @deprecated Use {@link Directives} instead, specifically {@link Directives#BUILT_IN_DIRECTIVES},
 * {@link Directives#BUILT_IN_DIRECTIVES_MAP}, and {@link Directives#isBuiltInDirective(String)}.
 */
@Deprecated
@PublicApi
public class DirectiveInfo {

    /**
     * A set of directives which provided by graphql specification
     *
     * @deprecated Use {@link Directives#BUILT_IN_DIRECTIVES} instead.
     */
    @Deprecated
    public static final Set<GraphQLDirective> GRAPHQL_SPECIFICATION_DIRECTIVES = Directives.BUILT_IN_DIRECTIVES;

    /**
     * A map from directive name to directive which provided by specification
     *
     * @deprecated Use {@link Directives#BUILT_IN_DIRECTIVES_MAP} instead.
     */
    @Deprecated
    public static final Map<String, GraphQLDirective> GRAPHQL_SPECIFICATION_DIRECTIVE_MAP = Directives.BUILT_IN_DIRECTIVES_MAP;

    /**
     * Returns true if a directive with provided directiveName has been defined in graphql specification
     *
     * @param directiveName the name of directive in question
     *
     * @return true if the directive provided by graphql specification, and false otherwise
     *
     * @deprecated Use {@link Directives#isBuiltInDirective(String)} instead.
     */
    @Deprecated
    public static boolean isGraphqlSpecifiedDirective(String directiveName) {
        return Directives.isBuiltInDirective(directiveName);
    }

    /**
     * Returns true if the provided directive has been defined in graphql specification
     *
     * @param graphQLDirective the directive in question
     *
     * @return true if the directive provided by graphql specification, and false otherwise
     *
     * @deprecated Use {@link Directives#isBuiltInDirective(GraphQLDirective)} instead.
     */
    @Deprecated
    public static boolean isGraphqlSpecifiedDirective(GraphQLDirective graphQLDirective) {
        return Directives.isBuiltInDirective(graphQLDirective);
    }
}
