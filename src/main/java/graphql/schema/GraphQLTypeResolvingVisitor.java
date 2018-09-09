package graphql.schema;

import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Map;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;

@Internal
public class GraphQLTypeResolvingVisitor extends GraphQLTypeVisitorStub {
    protected final Map<String, GraphQLType> typeMap;

    public GraphQLTypeResolvingVisitor(Map<String, GraphQLType> typeMap) {
        this.typeMap = typeMap;
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLType> context) {

        node.replaceInterfaces(node.getInterfaces().stream()
                .map(type -> (GraphQLOutputType) typeMap.get(type.getName()))
                .collect(Collectors.toList()));
        return super.visitGraphQLObjectType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLType> context) {

        node.replaceTypes(node.getTypes().stream()
                .map(type -> (GraphQLOutputType) typeMap.get(type.getName()))
                .collect(Collectors.toList()));
        return super.visitGraphQLUnionType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLType> context) {

        final GraphQLType resolvedType = typeMap.get(node.getName());
        assertNotNull(resolvedType, "type %s not found in schema", node.getName());
        context.getParentContext().thisNode().accept(context, new TypeRefResolvingVisitor(resolvedType));
        return super.visitGraphQLTypeReference(node, context);
    }


    private class TypeRefResolvingVisitor extends GraphQLTypeVisitorStub {
        protected final GraphQLType resolvedType;

        TypeRefResolvingVisitor(GraphQLType resolvedType) {
            this.resolvedType = resolvedType;
        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLType> context) {
            node.replaceType((GraphQLOutputType) resolvedType);
            return super.visitGraphQLFieldDefinition(node, context);
        }

        @Override
        public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLType> context) {
            node.replaceType((GraphQLInputType) resolvedType);
            return super.visitGraphQLArgument(node, context);
        }

        @Override
        public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLType> context) {
            node.replaceType((GraphQLInputType) resolvedType);
            return super.visitGraphQLInputObjectField(node, context);
        }

        @Override
        public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLType> context) {
            node.replaceType(resolvedType);
            return super.visitGraphQLList(node, context);
        }

        @Override
        public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLType> context) {
            node.replaceType(resolvedType);
            return super.visitGraphQLNonNull(node, context);
        }
    }
}
