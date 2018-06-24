package graphql;


import graphql.schema.GraphQLDirective;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_SPREAD;
import static graphql.introspection.Introspection.DirectiveLocation.INLINE_FRAGMENT;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLNonNull.nonNull;

/**
 * The query directives that are under stood by graphql-java
 */
public class Directives {

    public static final GraphQLDirective IncludeDirective = GraphQLDirective.newDirective()
            .name("include")
            .description("Directs the executor to include this field or fragment only when the `if` argument is true")
            .argument(newArgument()
                    .name("if")
                    .type(nonNull(GraphQLBoolean))
                    .description("Included when true."))
            .validLocations(FRAGMENT_SPREAD, INLINE_FRAGMENT, FIELD)
            .build();

    public static final GraphQLDirective SkipDirective = GraphQLDirective.newDirective()
            .name("skip")
            .description("Directs the executor to skip this field or fragment when the `if`'argument is true.")
            .argument(newArgument()
                    .name("if")
                    .type(nonNull(GraphQLBoolean))
                    .description("Skipped when true."))
            .validLocations(FRAGMENT_SPREAD, INLINE_FRAGMENT, FIELD)
            .build();

    /**
     * @deprecated - this is no longer needed and will be removed in a future version
     */
    @Deprecated
    public static final GraphQLDirective FetchDirective = GraphQLDirective.newDirective()
            .name("fetch")
            .description("Directs the SDL type generation to create a data fetcher that uses this `from` argument as the property name")
            .argument(newArgument()
                    .name("from")
                    .type(nonNull(GraphQLString))
                    .description("The `name` used to fetch values from the underlying object"))
            .validLocations(FIELD_DEFINITION)
            .build();

    public static final GraphQLDirective DeferDirective = GraphQLDirective.newDirective()
            .name("defer")
            .description("This directive allows results to be deferred during execution")
            .validLocations(FIELD)
            .build();

}
