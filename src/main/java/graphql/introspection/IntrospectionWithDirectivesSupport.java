package graphql.introspection;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import graphql.PublicApi;
import graphql.PublicSpi;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.introspection.Introspection.__Directive;
import static graphql.introspection.Introspection.__EnumValue;
import static graphql.introspection.Introspection.__Field;
import static graphql.introspection.Introspection.__InputValue;
import static graphql.introspection.Introspection.__Type;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TreeTransformerUtil.changeNode;

@PublicApi
public class IntrospectionWithDirectivesSupport {

    private static final Set<String> TARGETED_TYPES = ImmutableSet.of(
            __Field.getName(), __Type.getName(), __EnumValue.getName(), __InputValue.getName()
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
        GraphQLType __Directive = schema.getType(Introspection.__Directive.getName());
        GraphQLObjectType __DirectiveExtensions = newObject().name("__DirectiveExtensions")
                .field(fld -> fld
                        .name("directives")
                        .type(nonNull(list(__Directive))))
                .build();

        return SchemaTransformer.transformSchema(schema, new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                if (TARGETED_TYPES.contains(objectType.getName())) {
                    GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                    GraphQLObjectType newObjectType = addDirectiveExtensions(objectType, codeRegistry, __DirectiveExtensions);
                    return changeNode(context, newObjectType);
                }
                return CONTINUE;
            }
        });
    }

    private GraphQLObjectType addDirectiveExtensions(GraphQLObjectType objectType, GraphQLCodeRegistry.Builder codeRegistry, GraphQLOutputType __DirectiveExtensions) {
        objectType = objectType.transform(bld -> bld.field(fld -> fld.name("extensions").type(__DirectiveExtensions)));
        DataFetcher<?> extDF = env -> {
            final GraphQLType type = env.getSource();
            List<GraphQLDirective> directives = Collections.emptyList();
            if (type instanceof GraphQLDirectiveContainer) {
                directives = ((GraphQLDirectiveContainer) type).getDirectives();
            }
            directives = filterDirectives(type, directives);
            return ImmutableMap.of("directives", directives);
        };
        codeRegistry.dataFetcher(coordinates(objectType.getName(), "extensions"), extDF);
        return objectType;
    }

    private List<GraphQLDirective> filterDirectives(GraphQLType type, List<GraphQLDirective> directives) {
        return directives.stream().filter(directive -> directivePredicate.isDirectiveIncluded(new DirectivePredicateEnvironment() {
            @Override
            public GraphQLType getType() {
                return type;
            }

            @Override
            public GraphQLDirective getDirective() {
                return directive;
            }
        })).collect(Collectors.toList());
    }

    /**
     *
     */

    @PublicApi
    interface DirectivePredicateEnvironment {
        GraphQLType getType();

        GraphQLDirective getDirective();
    }


    @PublicSpi
    @FunctionalInterface
    interface DirectivePredicate {
        boolean isDirectiveIncluded(DirectivePredicateEnvironment environment);
    }
}
