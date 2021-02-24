package graphql.introspection;

import com.google.common.collect.ImmutableMap;
import graphql.PublicApi;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TreeTransformerUtil.changeNode;

@PublicApi
public class IntrospectionWithDirectivesSupport {

    public GraphQLSchema apply(GraphQLSchema schema) {
        GraphQLSchema newSchema = SchemaTransformer.transformSchema(schema, new GraphQLTypeVisitorStub() {
            @Override
            public TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                if (objectType.getName().startsWith("Query")) {
                    GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                    GraphQLObjectType newObjectType = tweakIntrospectionType(objectType, codeRegistry);
                    if (newObjectType != objectType) {
                        return changeNode(context, objectType);
                    }
                }
                return CONTINUE;
            }
        });
        return newSchema;
    }

    private static final GraphQLObjectType __DIRECTIVE_EXTENSIONS = newObject().name("__DirectiveExtensions")
            .field(fld -> fld
                    .name("directives")
                    .type(nonNull(list(Introspection.__Directive))))
            .build();

    private GraphQLObjectType tweakIntrospectionType(GraphQLObjectType objectType, GraphQLCodeRegistry.Builder codeRegistry) {
        //if (objectType.getName().equals(Introspection.__Field.getName())) {
        if (objectType.getName().equals("Query")) {
            objectType = objectType.transform(bld -> bld.field(fld -> fld.name("extensions").type(__DIRECTIVE_EXTENSIONS)));
            DataFetcher<?> extDF = env -> {
                return ImmutableMap.of("extensions", null);
            };
            codeRegistry.dataFetcher(FieldCoordinates.coordinates(Introspection.__Field.getName(), "extensions"), extDF);
        }
        return objectType;
    }

}
