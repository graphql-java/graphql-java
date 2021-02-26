package graphql.introspection;

import com.google.common.collect.ImmutableMap;
import graphql.PublicApi;
import graphql.PublicSpi;
import graphql.Scalars;
import graphql.language.AstPrinter;
import graphql.language.AstValueHelper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.List;
import java.util.function.Consumer;

import static graphql.introspection.Introspection.__Directive;
import static graphql.introspection.Introspection.__EnumValue;
import static graphql.introspection.Introspection.__Field;
import static graphql.introspection.Introspection.__InputValue;
import static graphql.introspection.Introspection.__Type;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLTypeReference.typeRef;
import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TreeTransformerUtil.changeNode;
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
 * An `extension` field is added and this contains extra fields that hold the directives.
 *
 * For example the `__Field` type becomes as follows:
 *
 * <pre>
 *      type __Field {
 *          name: String!
 *          // other fields ...
 *          extensions: __FieldExtensions
 *      }
 *
 *      type __FieldExtensions {
 *          directives: [__Directive]!
 *      }
 *  </pre>
 *
 * `__Field`, `__Type` and `__Enum` are all enhanced with this `extensions: __XXXXExtensions` field.
 *
 * The `__InputValue` is also enhanced however it has two extra fields.  That is if the argument is a directive
 * argument then it has a `value` field in AST format along with any directives it may contain.
 *
 * <pre>
 *      type __InputValue {
 *          name: String!
 *          // other field
 *          extensions: __InputValueExtensions
 *      }
 *
 *      type __InputValueExtensions {
 *          directives: [__Directive]!
 *          value: String
 *      }
 * </pre>
 */
@PublicApi
public class IntrospectionWithDirectivesSupport {

    private final DirectivePredicate directivePredicate;

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
        // we need to build out custom types scoped to a schema
        GraphQLObjectType __TypeExtensions = newObject().name("__TypeExtensions")
                .field(fld -> fld
                        .name("directives")
                        .type(nonNull(list(nonNull(typeRef(__Directive.getName()))))))
                .build();

        GraphQLObjectType __FieldExtensions = newObject().name("__FieldExtensions")
                .field(fld -> fld
                        .name("directives")
                        .type(nonNull(list(nonNull(typeRef(__Directive.getName()))))))
                .build();

        GraphQLObjectType __EnumValueExtensions = newObject().name("__EnumValueExtensions")
                .field(fld -> fld
                        .name("directives")
                        .type(nonNull(list(nonNull(typeRef(__Directive.getName()))))))
                .build();

        GraphQLObjectType __InputValueExtensions = newObject().name("__InputValueExtensions")
                .field(fld -> fld
                        .name("value")
                        .type(Scalars.GraphQLString))
                .field(fld -> fld
                        .name("directives")
                        .type(nonNull(list(nonNull(typeRef(__Directive.getName()))))))
                .build();

        GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                // we need to change __XXX introspection types to have directive extensions
                if (__Type.getName().equals(objectType.getName())) {
                    GraphQLObjectType newObjectType = addDirectiveExtensions(objectType, codeRegistry, __TypeExtensions);
                    return changeNode(context, newObjectType);
                }
                if (__Field.getName().equals(objectType.getName())) {
                    GraphQLObjectType newObjectType = addDirectiveExtensions(objectType, codeRegistry, __FieldExtensions);
                    return changeNode(context, newObjectType);
                }
                if (__EnumValue.getName().equals(objectType.getName())) {
                    GraphQLObjectType newObjectType = addDirectiveExtensions(objectType, codeRegistry, __EnumValueExtensions);
                    return changeNode(context, newObjectType);
                }
                // we need to change __InputValue to have value and directives as extensions
                if (__InputValue.getName().equals(objectType.getName())) {
                    GraphQLObjectType newObjectType = addInputValueValueExtensions(objectType, codeRegistry, __InputValueExtensions);
                    return changeNode(context, newObjectType);
                }
                return CONTINUE;
            }
        };
        Consumer<GraphQLSchema.Builder> afterTransform = builder -> builder
                .additionalType(__TypeExtensions)
                .additionalType(__FieldExtensions)
                .additionalType(__EnumValueExtensions)
                .additionalType(__InputValueExtensions);
        return SchemaTransformer.transformSchema(schema, visitor, afterTransform);
    }

    private GraphQLObjectType addDirectiveExtensions(GraphQLObjectType objectType, GraphQLCodeRegistry.Builder codeRegistry, GraphQLObjectType extensionsType) {
        objectType = objectType.transform(bld -> bld.field(fld -> fld.name("extensions").type(typeRef(extensionsType.getName()))));
        DataFetcher<?> extDF = env -> {
            final GraphQLDirectiveContainer type = env.getSource();
            List<GraphQLDirective> directives = filterDirectives(type);
            return ImmutableMap.of("directives", directives);
        };
        codeRegistry.dataFetcher(coordinates(objectType.getName(), "extensions"), extDF);
        return objectType;
    }

    private GraphQLObjectType addInputValueValueExtensions(GraphQLObjectType __InputValue, GraphQLCodeRegistry.Builder codeRegistry, GraphQLObjectType __InputValueExtensions) {
        __InputValue = __InputValue.transform(bld -> bld.field(fld -> fld.name("extensions").type(typeRef(__InputValueExtensions.getName()))));
        DataFetcher<?> extDF = env -> {
            final Object source = env.getSource();
            if (source instanceof GraphQLArgument) {
                GraphQLArgument argument = (GraphQLArgument) source;
                ImmutableMap.Builder<String, Object> returned = ImmutableMap.builder();

                // arguments have directives
                List<GraphQLDirective> directives = filterDirectives(argument);
                returned.put("directives", directives);

                // and if we are looking at directives arguments then they have values.  But input fields and field arguments are also __InputValue
                // so we have to be careful here - input fields and field args have no values - only directives on types and fields have values
                if (isForDirectivesFetch(env)) {
                    Object value = argument.getValue();
                    String valueStr = AstPrinter.printAst(AstValueHelper.astFromValue(value, argument.getType()));
                    returned.put("value", valueStr);
                }
                return returned.build();
            }
            if (source instanceof GraphQLInputObjectField) {
                GraphQLInputObjectField inputField = (GraphQLInputObjectField) source;

                List<GraphQLDirective> directives = filterDirectives(inputField);
                return ImmutableMap.of("directives", directives);
            }
            return null;
        };
        codeRegistry.dataFetcher(coordinates(__InputValue.getName(), "extensions"), extDF);
        return __InputValue;
    }

    private List<GraphQLDirective> filterDirectives(GraphQLDirectiveContainer container) {
        return container.getDirectives().stream().filter(directive -> {
            DirectivePredicateEnvironment env = new DirectivePredicateEnvironment() {
                @Override
                public GraphQLDirectiveContainer getContainingType() {
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

    private boolean isForDirectivesFetch(DataFetchingEnvironment env) {
        // we MUST be under /?/?/args/extensions - so is it a directives set of args or an input field set of __InputValue??
        List<String> keysOnly = env.getExecutionStepInfo().getPath().getKeysOnly();
        return keysOnly.contains("directives");
    }

    /**
     * The parameter environment on a call to {@link DirectivePredicate}
     */
    @PublicApi
    interface DirectivePredicateEnvironment {

        /**
         * @return the schema element that contained this directive
         */
        GraphQLDirectiveContainer getContainingType();

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
