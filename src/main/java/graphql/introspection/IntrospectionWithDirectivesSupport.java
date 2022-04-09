package graphql.introspection;

import com.google.common.collect.ImmutableSet;
import graphql.DirectivesUtil;
import graphql.PublicApi;
import graphql.PublicSpi;
import graphql.execution.ValuesResolver;
import graphql.language.AstPrinter;
import graphql.language.Node;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.__EnumValue;
import static graphql.introspection.Introspection.__Field;
import static graphql.introspection.Introspection.__InputValue;
import static graphql.introspection.Introspection.__Schema;
import static graphql.introspection.Introspection.__Type;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.util.TraversalControl.CONTINUE;
import static java.util.stream.Collectors.toList;

/**
 * The graphql specification does not allow you to retrieve the directives and their argument values that
 * are present on types, enums, fields and input fields, so this class allows you to change the schema
 * and enhance the Introspection types to contain this information.
 *
 * This allows you to get a directive say like `@example(argName : "someValue")` that is on a field or type
 * at introspection time and act on it.
 *
 * This class takes a predicate that allows you to filter the directives you want to expose to the world.
 *
 * An `appliedDirectives` field is added and this contains extra fields that hold the directives and their applied values.
 *
 * For example the `__Field` type becomes as follows:
 *
 * <pre>
 *      type __Field {
 *          name: String!
 *          // other fields ...
 *          appliedDirectives: [_AppliedDirective!]!   // NEW FIELD
 *      }
 *
 *     type _AppliedDirective {                        // NEW INTROSPECTION TYPE
 *          name: String!
 *          args: [_DirectiveArgument!]!
 *      }
 *
 *      type _DirectiveArgument {                      // NEW INTROSPECTION TYPE
 *          name: String!
 *          value: String!
 *      }
 *  </pre>
 */
@PublicApi
public class IntrospectionWithDirectivesSupport {

    private final DirectivePredicate directivePredicate;
    private final String typePrefix;

    private final Set<String> INTROSPECTION_ELEMENTS = ImmutableSet.of(
            __Schema.getName(), __Type.getName(), __Field.getName(), __EnumValue.getName(), __InputValue.getName()
    );


    /**
     * This version lists all directives on a schema element
     */
    public IntrospectionWithDirectivesSupport() {
        this(env -> true);
    }

    /**
     * This version allows you to filter what directives are listed via the provided predicate
     *
     * @param directivePredicate the filtering predicate to decide what directives are shown
     */
    public IntrospectionWithDirectivesSupport(DirectivePredicate directivePredicate) {
        this(directivePredicate, "_");
    }

    /**
     * This version allows you to filter what directives are listed via the provided predicate
     *
     * Some graphql systems (graphql-js in 2021) cannot cope with extra types starting with `__`
     * so we use a `_` as a prefix by default.   You can supply your own prefix via this constructor.
     *
     * See: https://github.com/graphql-java/graphql-java/pull/2221 for more details
     *
     * @param directivePredicate the filtering predicate to decide what directives are shown
     * @param typePrefix         the prefix to put on the new `AppliedDirectives` type
     */
    public IntrospectionWithDirectivesSupport(DirectivePredicate directivePredicate, String typePrefix) {
        this.directivePredicate = assertNotNull(directivePredicate);
        this.typePrefix = assertNotNull(typePrefix);
    }

    /**
     * This will transform the schema to have the new extension shapes
     *
     * @param schema the original schema
     *
     * @return the transformed schema with new extension types and fields in it for Introspection
     */
    public GraphQLSchema apply(GraphQLSchema schema) {
        GraphQLObjectType directiveArgumentType = mkDirectiveArgumentType(typePrefix + "DirectiveArgument");
        GraphQLObjectType appliedDirectiveType = mkAppliedDirectiveType(typePrefix + "AppliedDirective", directiveArgumentType);
        GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                // we need to change __XXX introspection types to have directive extensions
                if (INTROSPECTION_ELEMENTS.contains(objectType.getName())) {
                    GraphQLObjectType newObjectType = addAppliedDirectives(objectType, codeRegistry, appliedDirectiveType, directiveArgumentType);
                    return changeNode(context, newObjectType);
                }
                return CONTINUE;
            }
        };
        GraphQLSchema newSchema = SchemaTransformer.transformSchema(schema, visitor);
        return addDirectiveDefinitionFilter(newSchema);
    }

    private GraphQLObjectType mkDirectiveArgumentType(String name) {
        return newObject().name(name)
                .description("Directive arguments can have names and values.  The values are in graphql SDL syntax printed as a string." +
                        " This type is NOT specified by the graphql specification presently.")
                .field(fld -> fld
                        .name("name")
                        .type(nonNull(GraphQLString)))
                .field(fld -> fld
                        .name("value")
                        .type(nonNull(GraphQLString)))
                .build();
    }

    private GraphQLObjectType mkAppliedDirectiveType(String name, GraphQLType directiveArgumentType) {
        return newObject().name(name)
                .description("An Applied Directive is an instances of a directive as applied to a schema element." +
                        " This type is NOT specified by the graphql specification presently.")
                .field(fld -> fld
                        .name("name")
                        .type(nonNull(GraphQLString)))
                .field(fld -> fld
                        .name("args")
                        .type(nonNull(list(nonNull(directiveArgumentType)))))
                .build();
    }

    private GraphQLSchema addDirectiveDefinitionFilter(GraphQLSchema schema) {
        DataFetcher<?>  df = env -> {
            List<GraphQLDirective> definedDirectives = env.getGraphQLSchema().getDirectives();
            return filterDirectives(schema,true, null, definedDirectives);
        };
        GraphQLCodeRegistry codeRegistry = schema.getCodeRegistry().transform(bld ->
                bld.dataFetcher(coordinates(__Schema, "directives"), df));
        return schema.transform(bld -> bld.codeRegistry(codeRegistry));
    }

    private GraphQLObjectType addAppliedDirectives(GraphQLObjectType originalType, GraphQLCodeRegistry.Builder codeRegistry, GraphQLObjectType appliedDirectiveType, GraphQLObjectType directiveArgumentType) {
        GraphQLObjectType objectType = originalType.transform(bld -> bld.field(fld -> fld.name("appliedDirectives").type(nonNull(list(nonNull(appliedDirectiveType))))));
        DataFetcher<?> df = env -> {
            Object source = env.getSource();
            GraphQLSchema schema = env.getGraphQLSchema();
            if (source instanceof GraphQLDirectiveContainer) {
                GraphQLDirectiveContainer type = env.getSource();
                List<GraphQLAppliedDirective> appliedDirectives = DirectivesUtil.toAppliedDirectives(type);
                return filterAppliedDirectives(schema, false, type, appliedDirectives);
            }
            if (source instanceof GraphQLSchema) {
                List<GraphQLAppliedDirective> appliedDirectives = DirectivesUtil.toAppliedDirectives(schema.getSchemaAppliedDirectives(), schema.getSchemaDirectives());
                return filterAppliedDirectives(schema, true, null, appliedDirectives);
            }
            return assertShouldNeverHappen("What directive containing element have we not considered? - %s", originalType);
        };
        DataFetcher<?> argsDF = env -> {
            final GraphQLAppliedDirective directive = env.getSource();
            // we only show directive arguments that have values set on them
            return directive.getArguments().stream()
                    .filter(arg -> arg.getArgumentValue().isSet());
        };
        DataFetcher<?> argValueDF = env -> {
            final GraphQLAppliedDirectiveArgument argument = env.getSource();
            InputValueWithState value = argument.getArgumentValue();
            Node<?> literal = ValuesResolver.valueToLiteral(value, argument.getType());
            return AstPrinter.printAst(literal);
        };
        codeRegistry.dataFetcher(coordinates(objectType, "appliedDirectives"), df);
        codeRegistry.dataFetcher(coordinates(appliedDirectiveType, "args"), argsDF);
        codeRegistry.dataFetcher(coordinates(directiveArgumentType, "value"), argValueDF);
        return objectType;
    }

    private List<GraphQLDirective> filterDirectives(GraphQLSchema schema, boolean isDefinedDirective, GraphQLDirectiveContainer container, List<GraphQLDirective> directives) {
        return directives.stream().filter(directive -> {
            DirectivePredicateEnvironment env = buildDirectivePredicateEnv(schema, isDefinedDirective, container, directive.getName());
            return directivePredicate.isDirectiveIncluded(env);
        }).collect(toList());
    }

    private List<GraphQLAppliedDirective> filterAppliedDirectives(GraphQLSchema schema, boolean isDefinedDirective, GraphQLDirectiveContainer container, List<GraphQLAppliedDirective> directives) {
        return directives.stream().filter(directive -> {
            DirectivePredicateEnvironment env = buildDirectivePredicateEnv(schema, isDefinedDirective, container, directive.getName());
            return directivePredicate.isDirectiveIncluded(env);
        }).collect(toList());
    }

    @NotNull
    private DirectivePredicateEnvironment buildDirectivePredicateEnv(GraphQLSchema schema, boolean isDefinedDirective, GraphQLDirectiveContainer container, String directiveName) {
        return new DirectivePredicateEnvironment() {
            @Override
            public GraphQLDirectiveContainer getDirectiveContainer() {
                return container;
            }

            @Override
            public boolean isDefinedDirective() {
                return isDefinedDirective;
            }

            @Override
            public String getDirectiveName() {
                return directiveName;
            }

            @Override
            public GraphQLSchema getSchema() {
                return schema;
            }
        };
    }

    /**
     * The parameter environment on a call to {@link DirectivePredicate}
     */
    @PublicApi
    public interface DirectivePredicateEnvironment {

        /**
         * The schema element that contained this directive.  If this is a {@link GraphQLSchema}
         * then this will be null.  This is the only case where this is true.
         *
         * @return the schema element that contained this directive.
         */
        GraphQLDirectiveContainer getDirectiveContainer();

        /**
         * @return the directive to be included
         */
        String getDirectiveName();

        /**
         * A schema has two list of directives.  A list of directives that are defined
         * in that schema and the list of directives that are applied to a schema element.
         *
         * This returns true if this filtering represents the defined directives.
         *
         * @return true if this is filtering defined directives
         */
        boolean isDefinedDirective();

        /**
         * @return graphql schema in place
         */
        GraphQLSchema getSchema();
    }


    /**
     * A callback which allows you to decide what directives may be included
     * in introspection extensions
     */
    @PublicSpi
    @FunctionalInterface
    public interface DirectivePredicate {
        /**
         * Return true if the directive should be included
         *
         * @param environment the callback parameters
         *
         * @return true if the directive should be included
         */
        boolean isDirectiveIncluded(DirectivePredicateEnvironment environment);
    }
}
