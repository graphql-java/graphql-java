package graphql.introspection;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import graphql.PublicApi;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Set;

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

    Set<String> UNDERSCORE_TYPES = ImmutableSet.of(
            //__Field.getName(), __Type.getName(), __InputValue.getName(), __EnumValue.getName()
            __Field.getName(), __Type.getName()
    );

    GraphQLObjectType __DIRECTIVE_EXTENSIONS = newObject().name("__DirectiveExtensions")
            .field(fld -> fld
                    .name("directives")
                    .type(nonNull(list(__Directive))))
            .build();

    public GraphQLSchema apply(GraphQLSchema schema) {
        GraphQLSchema newSchema = SchemaTransformer.transformSchema(schema, new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                if (UNDERSCORE_TYPES.contains(objectType.getName())) {
                    GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                    GraphQLObjectType newObjectType = addDirectiveExtensions(objectType, codeRegistry);
                    return changeNode(context, newObjectType);
                }
                return CONTINUE;
            }
        });
        return newSchema;
    }


    private GraphQLObjectType addDirectiveExtensions(GraphQLObjectType objectType, GraphQLCodeRegistry.Builder codeRegistry) {
        objectType = objectType.transform(bld -> bld.field(fld -> fld.name("extensions").type(__DIRECTIVE_EXTENSIONS)));
        DataFetcher<?> extDF = env -> {
            GraphQLDirectiveContainer directiveContainer = env.getSource();
            return ImmutableMap.of("extensions", ImmutableMap.of("directives", directiveContainer.getDirectives()));
        };
        codeRegistry.dataFetcher(coordinates(__Field.getName(), "extensions"), extDF);
        return objectType;
    }

}
