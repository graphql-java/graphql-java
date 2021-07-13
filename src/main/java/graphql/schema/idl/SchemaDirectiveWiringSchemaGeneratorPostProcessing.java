package graphql.schema.idl;

import graphql.Internal;
import graphql.language.NamedNode;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.schema.SchemaTransformer;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static graphql.util.TraversalControl.CONTINUE;

@Internal
class SchemaDirectiveWiringSchemaGeneratorPostProcessing implements SchemaGeneratorPostProcessing {

    private final SchemaGeneratorDirectiveHelper generatorDirectiveHelper = new SchemaGeneratorDirectiveHelper();
    private final TypeDefinitionRegistry typeRegistry;
    private final RuntimeWiring runtimeWiring;
    private final GraphQLCodeRegistry.Builder codeRegistryBuilder;
    private final Map<String, Object> directiveBehaviourContext = new HashMap<>();


    public SchemaDirectiveWiringSchemaGeneratorPostProcessing(TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring, GraphQLCodeRegistry.Builder codeRegistryBuilder) {
        this.typeRegistry = typeRegistry;
        this.runtimeWiring = runtimeWiring;
        this.codeRegistryBuilder = codeRegistryBuilder;
    }


    @Override
    public GraphQLSchema process(GraphQLSchema originalSchema) {
        codeRegistryBuilder.trackChanges();
        Visitor visitor = new Visitor();
        GraphQLSchema newSchema = SchemaTransformer.transformSchema(originalSchema, visitor);
        if (visitor.schemaChanged() || codeRegistryBuilder.hasChanged()) {
            return newSchema.transform(builder -> {
                // they could have changed the code registry so rebuild it
                GraphQLCodeRegistry codeRegistry = this.codeRegistryBuilder.build();
                builder.codeRegistry(codeRegistry);
            });
        }
        return newSchema;
    }

    public class Visitor extends GraphQLTypeVisitorStub {

        private boolean schemaChanged = false;

        public boolean schemaChanged() {
            return schemaChanged;
        }

        private SchemaGeneratorDirectiveHelper.Parameters mkBehaviourParams() {
            return new SchemaGeneratorDirectiveHelper.Parameters(typeRegistry, runtimeWiring, directiveBehaviourContext, codeRegistryBuilder);
        }

        private TraversalControl changOrContinue(GraphQLSchemaElement node, GraphQLSchemaElement newNode, TraverserContext<GraphQLSchemaElement> context) {
            if (node != newNode) {
                TreeTransformerUtil.changeNode(context, newNode);
                schemaChanged = true;
            }
            return CONTINUE;
        }

        private boolean isIntrospectionType(GraphQLNamedType type) {
            return type.getName().startsWith("__");
        }

        private <T extends GraphQLNamedType> boolean notSuitable(T node, Function<T, NamedNode<?>> suitableFunc) {
            if (isIntrospectionType(node)) {
                return true;
            }
            NamedNode<?> definition = suitableFunc.apply(node);
            return definition == null;
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
            if (notSuitable(node, GraphQLObjectType::getDefinition)) {
                return CONTINUE;
            }
            GraphQLSchemaElement newNode = generatorDirectiveHelper.onObject(node, mkBehaviourParams());
            return changOrContinue(node, newNode, context);
        }

        @Override
        public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
            if (notSuitable(node, GraphQLInterfaceType::getDefinition)) {
                return CONTINUE;
            }
            GraphQLSchemaElement newNode = generatorDirectiveHelper.onInterface(node, mkBehaviourParams());
            return changOrContinue(node, newNode, context);
        }

        @Override
        public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context) {
            if (notSuitable(node, GraphQLEnumType::getDefinition)) {
                return CONTINUE;
            }
            GraphQLSchemaElement newNode = generatorDirectiveHelper.onEnum(node, mkBehaviourParams());
            return changOrContinue(node, newNode, context);
        }

        @Override
        public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
            if (notSuitable(node, GraphQLInputObjectType::getDefinition)) {
                return CONTINUE;
            }
            GraphQLSchemaElement newNode = generatorDirectiveHelper.onInputObjectType(node, mkBehaviourParams());
            return changOrContinue(node, newNode, context);
        }

        @Override
        public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
            if (notSuitable(node, GraphQLScalarType::getDefinition)) {
                return CONTINUE;
            }
            GraphQLSchemaElement newNode = generatorDirectiveHelper.onScalar(node, mkBehaviourParams());
            return changOrContinue(node, newNode, context);
        }

        @Override
        public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {
            if (notSuitable(node, GraphQLUnionType::getDefinition)) {
                return CONTINUE;
            }
            GraphQLSchemaElement newNode = generatorDirectiveHelper.onUnion(node, mkBehaviourParams());
            return changOrContinue(node, newNode, context);
        }
    }
}
