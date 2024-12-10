package graphql;


import graphql.language.*;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.*;
import static graphql.language.DirectiveLocation.newDirectiveLocation;
import static graphql.language.InputValueDefinition.newInputValueDefinition;
import static graphql.language.NonNullType.newNonNullType;
import static graphql.language.TypeName.newTypeName;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLNonNull.nonNull;

/**
 * The directives that are understood by graphql-java
 */
@PublicApi
public class Directives {

    private static final String DEPRECATED = "deprecated";
    private static final String INCLUDE = "include";
    private static final String SKIP = "skip";
    private static final String SPECIFIED_BY = "specifiedBy";
    private static final String ONE_OF = "oneOf";
    private static final String DEFER = "defer";
    private static final String ERROR_HANDLING = "errorHandling";

    public static final DirectiveDefinition DEPRECATED_DIRECTIVE_DEFINITION;
    public static final DirectiveDefinition INCLUDE_DIRECTIVE_DEFINITION;
    public static final DirectiveDefinition SKIP_DIRECTIVE_DEFINITION;
    public static final DirectiveDefinition SPECIFIED_BY_DIRECTIVE_DEFINITION;
    @ExperimentalApi
    public static final DirectiveDefinition ONE_OF_DIRECTIVE_DEFINITION;
    @ExperimentalApi
    public static final DirectiveDefinition DEFER_DIRECTIVE_DEFINITION;
    @ExperimentalApi
    public static final DirectiveDefinition ERROR_HANDLING_DIRECTIVE_DEFINITION;

    public static final String BOOLEAN = "Boolean";
    public static final String STRING = "String";
    public static final String NO_LONGER_SUPPORTED = "No longer supported";

    static {
        DEPRECATED_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(DEPRECATED)
                .directiveLocation(newDirectiveLocation().name(FIELD_DEFINITION.name()).build())
                .directiveLocation(newDirectiveLocation().name(ENUM_VALUE.name()).build())
                .directiveLocation(newDirectiveLocation().name(ARGUMENT_DEFINITION.name()).build())
                .directiveLocation(newDirectiveLocation().name(INPUT_FIELD_DEFINITION.name()).build())
                .description(createDescription("Marks the field, argument, input field or enum value as deprecated"))
                .inputValueDefinition(
                        newInputValueDefinition()
                                .name("reason")
                                .description(createDescription("The reason for the deprecation"))
                                .type(newNonNullType(newTypeName().name(STRING).build()).build())
                                .defaultValue(StringValue.newStringValue().value(NO_LONGER_SUPPORTED).build())
                                .build())
                .build();

        INCLUDE_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(INCLUDE)
                .directiveLocation(newDirectiveLocation().name(FRAGMENT_SPREAD.name()).build())
                .directiveLocation(newDirectiveLocation().name(INLINE_FRAGMENT.name()).build())
                .directiveLocation(newDirectiveLocation().name(FIELD.name()).build())
                .description(createDescription("Directs the executor to include this field or fragment only when the `if` argument is true"))
                .inputValueDefinition(
                        newInputValueDefinition()
                                .name("if")
                                .description(createDescription("Included when true."))
                                .type(newNonNullType(newTypeName().name(BOOLEAN).build()).build())
                                .build())
                .build();

        SKIP_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(SKIP)
                .directiveLocation(newDirectiveLocation().name(FRAGMENT_SPREAD.name()).build())
                .directiveLocation(newDirectiveLocation().name(INLINE_FRAGMENT.name()).build())
                .directiveLocation(newDirectiveLocation().name(FIELD.name()).build())
                .description(createDescription("Directs the executor to skip this field or fragment when the `if` argument is true."))
                .inputValueDefinition(
                        newInputValueDefinition()
                                .name("if")
                                .description(createDescription("Skipped when true."))
                                .type(newNonNullType(newTypeName().name(BOOLEAN).build()).build())
                                .build())
                .build();

        SPECIFIED_BY_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(SPECIFIED_BY)
                .directiveLocation(newDirectiveLocation().name(SCALAR.name()).build())
                .description(createDescription("Exposes a URL that specifies the behaviour of this scalar."))
                .inputValueDefinition(
                        newInputValueDefinition()
                                .name("url")
                                .description(createDescription("The URL that specifies the behaviour of this scalar."))
                                .type(newNonNullType(newTypeName().name(STRING).build()).build())
                                .build())
                .build();

        ONE_OF_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(ONE_OF)
                .directiveLocation(newDirectiveLocation().name(INPUT_OBJECT.name()).build())
                .description(createDescription("Indicates an Input Object is a OneOf Input Object."))
                .build();

        DEFER_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(DEFER)
                .directiveLocation(newDirectiveLocation().name(FRAGMENT_SPREAD.name()).build())
                .directiveLocation(newDirectiveLocation().name(INLINE_FRAGMENT.name()).build())
                .description(createDescription("This directive allows results to be deferred during execution"))
                .inputValueDefinition(
                        newInputValueDefinition()
                                .name("if")
                                .description(createDescription("Deferred behaviour is controlled by this argument"))
                                .type(newNonNullType(newTypeName().name(BOOLEAN).build()).build())
                                .defaultValue(BooleanValue.newBooleanValue(true).build())
                                .build())
                .inputValueDefinition(
                        newInputValueDefinition()
                                .name("label")
                                .description(createDescription("A unique label that represents the fragment being deferred"))
                                .type(newTypeName().name(STRING).build())
                                .build())
                .build();
        ERROR_HANDLING_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(ERROR_HANDLING)
                .directiveLocation(newDirectiveLocation().name(QUERY.name()).build())
                .directiveLocation(newDirectiveLocation().name(MUTATION.name()).build())
                .directiveLocation(newDirectiveLocation().name(SUBSCRIPTION.name()).build())
                .description(createDescription("This directive controls how to handle errors"))
                .inputValueDefinition(
                        newInputValueDefinition()
                                .name("onError")
                                .type(newNonNullType(newTypeName().name(Enums.ON_ERROR).build()).build())
                                .defaultValue(EnumValue.newEnumValue(Enums.ON_ERROR_PROPAGATE).build())
                                .build())
                .build();
    }

    /**
     * The @defer directive can be used to defer sending data for a fragment until later in the query.
     * This is an opt-in directive that is not available unless it is explicitly put into the schema.
     * <p>
     * This implementation is based on the state of <a href="https://github.com/graphql/graphql-spec/pull/742">Defer/Stream PR</a>
     * More specifically at the state of this
     * <a href="https://github.com/graphql/graphql-spec/commit/c630301560d9819d33255d3ba00f548e8abbcdc6">commit</a>
     * <p>
     * The execution behaviour should match what we get from running Apollo Server 4.9.5 with graphql-js v17.0.0-alpha.2
     */
    @ExperimentalApi
    public static final GraphQLDirective DeferDirective = GraphQLDirective.newDirective()
            .name(DEFER)
            .description("This directive allows results to be deferred during execution")
            .validLocations(FRAGMENT_SPREAD, INLINE_FRAGMENT)
            .argument(newArgument()
                    .name("if")
                    .type(nonNull(GraphQLBoolean))
                    .description("Deferred behaviour is controlled by this argument")
                    .defaultValueLiteral(BooleanValue.newBooleanValue(true).build())
            )
            .argument(newArgument()
                    .name("label")
                    .type(GraphQLString)
                    .description("A unique label that represents the fragment being deferred")
            )
            .definition(DEFER_DIRECTIVE_DEFINITION)
            .build();

    public static final GraphQLDirective IncludeDirective = GraphQLDirective.newDirective()
            .name(INCLUDE)
            .description("Directs the executor to include this field or fragment only when the `if` argument is true")
            .argument(newArgument()
                    .name("if")
                    .type(nonNull(GraphQLBoolean))
                    .description("Included when true."))
            .validLocations(FRAGMENT_SPREAD, INLINE_FRAGMENT, FIELD)
            .definition(INCLUDE_DIRECTIVE_DEFINITION)
            .build();

    public static final GraphQLDirective SkipDirective = GraphQLDirective.newDirective()
            .name(SKIP)
            .description("Directs the executor to skip this field or fragment when the `if` argument is true.")
            .argument(newArgument()
                    .name("if")
                    .type(nonNull(GraphQLBoolean))
                    .description("Skipped when true."))
            .validLocations(FRAGMENT_SPREAD, INLINE_FRAGMENT, FIELD)
            .definition(SKIP_DIRECTIVE_DEFINITION)
            .build();


    /**
     * The "deprecated" directive is special and is always available in a graphql schema
     * <p>
     * See <a href="https://spec.graphql.org/draft/#sec--deprecated">the GraphQL specification for @deprecated</a>
     */
    public static final GraphQLDirective DeprecatedDirective = GraphQLDirective.newDirective()
            .name(DEPRECATED)
            .description("Marks the field, argument, input field or enum value as deprecated")
            .argument(newArgument()
                    .name("reason")
                    .type(nonNull(GraphQLString))
                    .defaultValueProgrammatic(NO_LONGER_SUPPORTED)
                    .description("The reason for the deprecation"))
            .validLocations(FIELD_DEFINITION, ENUM_VALUE, ARGUMENT_DEFINITION, INPUT_FIELD_DEFINITION)
            .definition(DEPRECATED_DIRECTIVE_DEFINITION)
            .build();

    /**
     * The "specifiedBy" directive allows to provide a specification URL for a Scalar
     */
    public static final GraphQLDirective SpecifiedByDirective = GraphQLDirective.newDirective()
            .name(SPECIFIED_BY)
            .description("Exposes a URL that specifies the behaviour of this scalar.")
            .argument(newArgument()
                    .name("url")
                    .type(nonNull(GraphQLString))
                    .description("The URL that specifies the behaviour of this scalar."))
            .validLocations(SCALAR)
            .definition(SPECIFIED_BY_DIRECTIVE_DEFINITION)
            .build();

    @ExperimentalApi
    public static final GraphQLDirective OneOfDirective = GraphQLDirective.newDirective()
            .name(ONE_OF)
            .description("Indicates an Input Object is a OneOf Input Object.")
            .validLocations(INPUT_OBJECT)
            .definition(ONE_OF_DIRECTIVE_DEFINITION)
            .build();

    @ExperimentalApi
    public static final GraphQLDirective ErrorHandlingDirective = GraphQLDirective.newDirective()
            .name(ERROR_HANDLING)
            .description("This directive controls how to handle errors.")
            .argument(newArgument()
                    .name("onError")
                    .type(nonNull(GraphQLEnumType.newEnum()
                            .name(Enums.ON_ERROR)
                            .value(Enums.ON_ERROR_PROPAGATE)
                            .value(Enums.ON_ERROR_NULL)
                            .build()))
                    .defaultValueProgrammatic(Enums.ON_ERROR_PROPAGATE)
                    .description("The URL that specifies the behaviour of this scalar."))
            .validLocations(QUERY, MUTATION, SUBSCRIPTION)
            .definition(ERROR_HANDLING_DIRECTIVE_DEFINITION)
            .build();

    private static Description createDescription(String s) {
        return new Description(s, null, false);
    }
}
