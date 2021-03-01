package graphql.introspection;

import com.google.common.collect.ImmutableSet;
import graphql.PublicApi;
import graphql.PublicSpi;
import graphql.language.AstPrinter;
import graphql.language.AstValueHelper;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.List;
import java.util.Set;

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
 *          appliedDirectives: [__AppliedDirective!]!   // NEW FIELD
 *      }
 *
 *     type __AppliedDirective {                        // NEW INTROSPECTION TYPE
 *          name: String!
 *          args: [__DirectiveArgument!]!
 *      }
 *
 *      type __DirectiveArgument {                      // NEW INTROSPECTION TYPE
 *          name: String!
 *          value: String
 *      }
 *  </pre>
 */
@PublicApi
public class IntrospectionWithDirectivesSupport {

    private final DirectivePredicate directivePredicate;

    private final Set<String> INTROSPECTION_ELEMENTS = ImmutableSet.of(
            __Schema.getName(), __Type.getName(), __Field.getName(), __EnumValue.getName(), __InputValue.getName()
    );

    private final GraphQLObjectType __DirectiveArgument = newObject().name("__DirectiveArgument")
            .description("Directive arguments can have names and values.  The values are in graphql SDL syntax printed as a string." +
                    " This type is NOT specified by the graphql specification presently.")
            .field(fld -> fld
                    .name("name")
                    .type(nonNull(GraphQLString)))
            .field(fld -> fld
                    .name("value")
                    .type(GraphQLString))
            .build();

    private final GraphQLObjectType __AppliedDirective = newObject().name("__AppliedDirective")
            .description("An Applied Directive is an instances of a directive as applied to a schema element." +
                    " This type is NOT specified by the graphql specification presently.")
            .field(fld -> fld
                    .name("name")
                    .type(GraphQLString))
            .field(fld -> fld
                    .name("args")
                    .type(nonNull(list(nonNull(__DirectiveArgument)))))
            .build();

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
        this.directivePredicate = directivePredicate;
    }

    /**
     * This will transform the schema to have the new extension shapes
     *
     * @param schema the original schema
     *
     * @return the transformed schema with new extension types and fields in it for Introspection
     */
    public GraphQLSchema apply(GraphQLSchema schema) {
        GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                // we need to change __XXX introspection types to have directive extensions
                if (INTROSPECTION_ELEMENTS.contains(objectType.getName())) {
                    GraphQLObjectType newObjectType = addAppliedDirectives(objectType, codeRegistry);
                    return changedNode(newObjectType, context);
                }
                return CONTINUE;
            }
        };
        return SchemaTransformer.transformSchema(schema, visitor);
    }

    private GraphQLObjectType addAppliedDirectives(GraphQLObjectType originalType, GraphQLCodeRegistry.Builder codeRegistry) {
        GraphQLObjectType objectType = originalType.transform(bld -> bld.field(fld -> fld.name("appliedDirectives").type(nonNull(list(nonNull(__AppliedDirective))))));
        DataFetcher<?> df = env -> {
            Object source = env.getSource();
            if (source instanceof GraphQLDirectiveContainer) {
                GraphQLDirectiveContainer type = env.getSource();
                return filterDirectives(type, type.getDirectives());
            }
            if (source instanceof GraphQLSchema) {
                GraphQLSchema schema = (GraphQLSchema) source;
                return filterDirectives(null, schema.getSchemaDirectives());
            }
            return assertShouldNeverHappen("What directive containing element have we not considered? - %s", originalType);
        };
        DataFetcher<?> argsDF = env -> {
            final GraphQLDirective directive = env.getSource();
            return directive.getArguments();
        };
        DataFetcher<?> argValueDF = env -> {
            final GraphQLArgument argument = env.getSource();
            Object value = argument.getValue();
            return AstPrinter.printAst(AstValueHelper.astFromValue(value, argument.getType()));
        };
        codeRegistry.dataFetcher(coordinates(objectType, "appliedDirectives"), df);
        codeRegistry.dataFetcher(coordinates(__AppliedDirective, "args"), argsDF);
        codeRegistry.dataFetcher(coordinates(__DirectiveArgument, "value"), argValueDF);
        return objectType;
    }

    private List<GraphQLDirective> filterDirectives(GraphQLDirectiveContainer container, List<GraphQLDirective> directives) {
        return directives.stream().filter(directive -> {
            DirectivePredicateEnvironment env = new DirectivePredicateEnvironment() {
                @Override
                public GraphQLDirectiveContainer getDirectiveContainer() {
                    return container;
                }

                @Override
                public GraphQLDirective getDirective() {
                    return directive;
                }
            };
            return directivePredicate.isDirectiveIncluded(env);
        }).collect(toList());
    }

    /**
     * The parameter environment on a call to {@link DirectivePredicate}
     */
    @PublicApi
    interface DirectivePredicateEnvironment {

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
        GraphQLDirective getDirective();
    }


    /**
     * A callback which allows you to decide what directives may be included
     * in introspection extensions
     */
    @PublicSpi
    @FunctionalInterface
    interface DirectivePredicate {
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
