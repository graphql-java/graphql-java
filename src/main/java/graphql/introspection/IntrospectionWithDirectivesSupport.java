package graphql.introspection;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.List;
import java.util.Set;
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

@PublicApi
public class IntrospectionWithDirectivesSupport {

    private static final Set<String> DIRECTIVE_TARGETED_TYPES = ImmutableSet.of(
            __Field.getName(), __Type.getName(), __EnumValue.getName()
    );

    private final DirectivePredicate directivePredicate;

    public IntrospectionWithDirectivesSupport() {
        this(env -> true);
    }

    public IntrospectionWithDirectivesSupport(DirectivePredicate directivePredicate) {
        this.directivePredicate = directivePredicate;
    }

    public GraphQLSchema apply(GraphQLSchema schema) {
        // we need to build out custom types scoped to a schema
        GraphQLObjectType __DirectiveExtensions = newObject().name("__DirectiveExtensions")
                .field(fld -> fld
                        .name("directives")
                        .type(nonNull(list(typeRef(__Directive.getName())))))
                .build();

        GraphQLObjectType __InputValueExtensions = newObject().name("__InputValueExtensions")
                .field(fld -> fld
                        .name("value")
                        .type(Scalars.GraphQLString))
                .field(fld -> fld
                        .name("directives")
                        .type(nonNull(list(typeRef(__Directive.getName())))))
                .build();

        GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                // we need to change __XXX introspection types to have directive extensions
                if (DIRECTIVE_TARGETED_TYPES.contains(objectType.getName())) {
                    GraphQLObjectType newObjectType = addDirectiveExtensions(objectType, codeRegistry, __DirectiveExtensions);
                    return changeNode(context, newObjectType);
                }
                // we need to change __Directive to have value as extensions
                if (__InputValue.getName().equals(objectType.getName())) {
                    GraphQLObjectType newObjectType = addInputValueValueExtensions(objectType, codeRegistry, __InputValueExtensions);
                    return changeNode(context, newObjectType);
                }
                return CONTINUE;
            }
        };
        Consumer<GraphQLSchema.Builder> afterTransform = builder -> builder
                .additionalType(__DirectiveExtensions)
                .additionalType(__InputValueExtensions);
        return SchemaTransformer.transformSchema(schema, visitor, afterTransform);
    }

    private GraphQLObjectType addDirectiveExtensions(GraphQLObjectType objectType, GraphQLCodeRegistry.Builder codeRegistry, GraphQLObjectType __DirectiveExtensions) {
        objectType = objectType.transform(bld -> bld.field(fld -> fld.name("extensions").type(typeRef(__DirectiveExtensions.getName()))));
        DataFetcher<?> extDF = env -> {
            final GraphQLDirectiveContainer type = env.getSource();
            List<GraphQLDirective> directives = type.getDirectives();
            directives = filterDirectives(type, directives);
            return ImmutableMap.of("directives", directives);
        };
        codeRegistry.dataFetcher(coordinates(objectType.getName(), "extensions"), extDF);
        return objectType;
    }

    private List<GraphQLDirective> filterDirectives(GraphQLDirectiveContainer type, List<GraphQLDirective> directives) {
        return directives.stream().filter(directive -> {
            DirectivePredicateEnvironment env = new DirectivePredicateEnvironment() {
                @Override
                public GraphQLDirectiveContainer getContainingType() {
                    return type;
                }

                @Override
                public GraphQLDirective getDirective() {
                    return directive;
                }
            };
            return directivePredicate.isDirectiveIncluded(env);
        }).collect(toList());
    }

    private GraphQLObjectType addInputValueValueExtensions(GraphQLObjectType __InputValue, GraphQLCodeRegistry.Builder codeRegistry, GraphQLObjectType __InputValueExtensions) {
        __InputValue = __InputValue.transform(bld -> bld.field(fld -> fld.name("extensions").type(typeRef(__InputValueExtensions.getName()))));
        DataFetcher<?> extDF = env -> {
            final Object source = env.getSource();
            if (source instanceof GraphQLArgument) {
                GraphQLArgument argument = (GraphQLArgument) source;
                ImmutableMap.Builder<String, Object> returned = ImmutableMap.builder();

                // arguments have directives
                List<GraphQLDirective> directives = argument.getDirectives();
                directives = filterDirectives(argument, directives);
                returned.put("directives", directives);

                // and if we are looking at directives arguments then they have values But input fields are also __InputValue
                // so we have to be careful here - input fields have no values - only directives on types and fields have values
                if (isForDirectivesFetch(env)) {
                    Object value = argument.getValue();
                    String valueStr = AstPrinter.printAst(AstValueHelper.astFromValue(value, argument.getType()));
                    returned.put("value", valueStr);
                }
                return returned.build();
            }
            return null;
        };
        codeRegistry.dataFetcher(coordinates(__InputValue.getName(), "extensions"), extDF);
        return __InputValue;
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
        boolean isDirectiveIncluded(DirectivePredicateEnvironment environment);
    }
}
