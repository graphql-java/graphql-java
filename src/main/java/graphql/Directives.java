package graphql;


import graphql.schema.GraphQLDirective;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_SPREAD;
import static graphql.introspection.Introspection.DirectiveLocation.INLINE_FRAGMENT;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLNonNull.nonNull;

/**
 * The directives that are understood by graphql-java
 */
@PublicApi
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
     * The @defer directive can be used to defer sending data for a field till later in the query.  This is an opt in
     * directive that is not available unless it is explicitly put into the schema.
     */
    public static final GraphQLDirective DeferDirective = GraphQLDirective.newDirective()
            .name("defer")
            .description("This directive allows results to be deferred during execution")
            .argument(newArgument()
                    .name("if")
                    .type(nonNull(GraphQLBoolean))
                    .description("Deferred behaviour is controlled by this argument")
                    .defaultValue(true)
            )
            .validLocations(FIELD)
            .build();

    /**
     * The "deprecated" directive is special and is always available in a graphql schema
     *
     * See https://graphql.github.io/graphql-spec/June2018/#sec--deprecated
     */
    public static final GraphQLDirective DeprecatedDirective = GraphQLDirective.newDirective()
            .name("deprecated")
            .description("Marks the field or enum value as deprecated")
            .argument(newArgument()
                    .name("reason")
                    .type(GraphQLString)
                    .defaultValue("No longer supported")
                    .description("The reason for the deprecation"))
            .validLocations(FIELD_DEFINITION, ENUM_VALUE)
            .build();

    /**
     * The "specifiedBy" directive allows to provide a specification URL for a Scalar
     */
    public static final GraphQLDirective SpecifiedByDirective = GraphQLDirective.newDirective()
            .name("specifiedBy")
            .description("Exposes a URL that specifies the behaviour of this scalar.")
            .argument(newArgument()
                    .name("url")
                    .type(nonNull(GraphQLString))
                    .description("The URL that specifies the behaviour of this scalar."))
            .validLocations(SCALAR)
            .build();

}
