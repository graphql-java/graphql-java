package graphql.schema;

import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.map;
import static graphql.util.TraversalControl.CONTINUE;

@Internal
public class GraphQLTypeResolvingVisitor extends GraphQLTypeVisitorStub {
    protected final Map<String, GraphQLNamedType> typeMap;

    public GraphQLTypeResolvingVisitor(Map<String, GraphQLNamedType> typeMap) {
        this.typeMap = typeMap;
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {

        node.replaceInterfaces(map(node.getInterfaces(), type -> (GraphQLNamedOutputType) typeMap.get(type.getName())));
        return super.visitGraphQLObjectType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
        node.replaceInterfaces(map(node.getInterfaces(), type -> (GraphQLNamedOutputType) typeMap.get(type.getName())));
        return super.visitGraphQLInterfaceType(node, context);
    }


    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {

        node.replaceTypes(map(node.getTypes(), type -> (GraphQLNamedOutputType) typeMap.get(type.getName())));
        return super.visitGraphQLUnionType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLSchemaElement> context) {
        return handleTypeReference(node, context);
    }

    public TraversalControl handleTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLSchemaElement> context) {
        final GraphQLType resolvedType = typeMap.get(node.getName());
        assertNotNull(resolvedType, () -> String.format("type %s not found in schema", node.getName()));
        context.getParentContext().thisNode().accept(context, new TypeRefResolvingVisitor(resolvedType));
        return CONTINUE;
    }

    @Override
    public TraversalControl visitBackRef(TraverserContext<GraphQLSchemaElement> context) {
        GraphQLSchemaElement schemaElement = context.thisNode();
        if (schemaElement instanceof GraphQLTypeReference) {
            return handleTypeReference((GraphQLTypeReference) schemaElement, context);
        }
        return CONTINUE;
    }

    private class TypeRefResolvingVisitor extends GraphQLTypeVisitorStub {
        protected final GraphQLType resolvedType;

        TypeRefResolvingVisitor(GraphQLType resolvedType) {
            this.resolvedType = resolvedType;
        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
            node.replaceType((GraphQLOutputType) resolvedType);
            return super.visitGraphQLFieldDefinition(node, context);
        }

        @Override
        public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
            node.replaceType((GraphQLInputType) resolvedType);
            return super.visitGraphQLArgument(node, context);
        }

        @Override
        public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
            node.replaceType((GraphQLInputType) resolvedType);
            return super.visitGraphQLAppliedDirectiveArgument(node, context);
        }

        @Override
        public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
            node.replaceType((GraphQLInputType) resolvedType);
            return super.visitGraphQLInputObjectField(node, context);
        }

        @Override
        public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLSchemaElement> context) {
            node.replaceType(resolvedType);
            return super.visitGraphQLList(node, context);
        }

        @Override
        public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLSchemaElement> context) {
            node.replaceType(resolvedType);
            return super.visitGraphQLNonNull(node, context);
        }
    }
}
