package graphql;


import graphql.language.BooleanValue;
import graphql.language.Description;
import graphql.language.DirectiveDefinition;
import graphql.language.StringValue;
import graphql.schema.GraphQLDirective;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_SPREAD;
import static graphql.introspection.Introspection.DirectiveLocation.INLINE_FRAGMENT;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.MUTATION;
import static graphql.introspection.Introspection.DirectiveLocation.QUERY;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.SUBSCRIPTION;
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
@NullMarked
public class Directives {

    private static final String DEPRECATED = "deprecated";
    private static final String INCLUDE = "include";
    private static final String SKIP = "skip";
    private static final String SPECIFIED_BY = "specifiedBy";
    private static final String ONE_OF = "oneOf";
    private static final String DEFER = "defer";
    private static final String EXPERIMENTAL_DISABLE_ERROR_PROPAGATION = "experimental_disableErrorPropagation";

    public static final DirectiveDefinition DEPRECATED_DIRECTIVE_DEFINITION;
    public static final DirectiveDefinition INCLUDE_DIRECTIVE_DEFINITION;
    public static final DirectiveDefinition SKIP_DIRECTIVE_DEFINITION;
    public static final DirectiveDefinition SPECIFIED_BY_DIRECTIVE_DEFINITION;
    @ExperimentalApi
    public static final DirectiveDefinition ONE_OF_DIRECTIVE_DEFINITION;
    @ExperimentalApi
    public static final DirectiveDefinition DEFER_DIRECTIVE_DEFINITION;
    @ExperimentalApi
    public static final DirectiveDefinition EXPERIMENTAL_DISABLE_ERROR_PROPAGATION_DIRECTIVE_DEFINITION;

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
        EXPERIMENTAL_DISABLE_ERROR_PROPAGATION_DIRECTIVE_DEFINITION = DirectiveDefinition.newDirectiveDefinition()
                .name(EXPERIMENTAL_DISABLE_ERROR_PROPAGATION)
                .directiveLocation(newDirectiveLocation().name(QUERY.name()).build())
                .directiveLocation(newDirectiveLocation().name(MUTATION.name()).build())
                .directiveLocation(newDirectiveLocation().name(SUBSCRIPTION.name()).build())
                .description(createDescription("This directive allows returning null in non-null positions that have an associated error"))
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
    public static final GraphQLDirective ExperimentalDisableErrorPropagationDirective = GraphQLDirective.newDirective()
            .name(EXPERIMENTAL_DISABLE_ERROR_PROPAGATION)
            .description("This directive disables error propagation when a non nullable field returns null for the given operation.")
            .validLocations(QUERY, MUTATION, SUBSCRIPTION)
            .definition(EXPERIMENTAL_DISABLE_ERROR_PROPAGATION_DIRECTIVE_DEFINITION)
            .build();

    /**
     * The set of all built-in directives that are always present in a graphql schema.
     * The iteration order is stable and meaningful.
     */
    public static final Set<GraphQLDirective> BUILT_IN_DIRECTIVES;

    /**
     * A map from directive name to directive for all built-in directives.
     */
    public static final Map<String, GraphQLDirective> BUILT_IN_DIRECTIVES_MAP;

    static {
        LinkedHashSet<GraphQLDirective> directives = new LinkedHashSet<>();
        directives.add(IncludeDirective);
        directives.add(SkipDirective);
        directives.add(DeprecatedDirective);
        directives.add(SpecifiedByDirective);
        directives.add(OneOfDirective);
        directives.add(DeferDirective);
        directives.add(ExperimentalDisableErrorPropagationDirective);
        BUILT_IN_DIRECTIVES = Collections.unmodifiableSet(directives);

        LinkedHashMap<String, GraphQLDirective> map = new LinkedHashMap<>();
        for (GraphQLDirective d : BUILT_IN_DIRECTIVES) {
            map.put(d.getName(), d);
        }
        BUILT_IN_DIRECTIVES_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Returns true if a directive with the provided name is a built-in directive.
     *
     * @param directiveName the name of the directive in question
     *
     * @return true if the directive is built-in, false otherwise
     */
    public static boolean isBuiltInDirective(String directiveName) {
        return BUILT_IN_DIRECTIVES_MAP.containsKey(directiveName);
    }

    /**
     * Returns true if the provided directive is a built-in directive.
     *
     * @param directive the directive in question
     *
     * @return true if the directive is built-in, false otherwise
     */
    public static boolean isBuiltInDirective(GraphQLDirective directive) {
        return isBuiltInDirective(directive.getName());
    }

    private static Description createDescription(String s) {
        return new Description(s, null, false);
    }

    private static final AtomicBoolean EXPERIMENTAL_DISABLE_ERROR_PROPAGATION_DIRECTIVE_ENABLED = new AtomicBoolean(true);

    /**
     * This can be used to get the state the `@experimental_disableErrorPropagation` directive support on a JVM wide basis .
     * @return true if the `@experimental_disableErrorPropagation` directive will be respected
     */
    public static boolean isExperimentalDisableErrorPropagationDirectiveEnabled() {
        return EXPERIMENTAL_DISABLE_ERROR_PROPAGATION_DIRECTIVE_ENABLED.get();
    }

    /**
     * This can be used to disable the `@experimental_disableErrorPropagation` directive support on a JVM wide basis in case your server
     * implementation does NOT want to act on this directive ever.
     *
     * @param flag the desired state of the flag
     */
    public static void setExperimentalDisableErrorPropagationEnabled(boolean flag) {
        EXPERIMENTAL_DISABLE_ERROR_PROPAGATION_DIRECTIVE_ENABLED.set(flag);
    }
}
