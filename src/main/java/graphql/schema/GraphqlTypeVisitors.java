package graphql.schema;

import graphql.Assert;
import graphql.AssertException;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.util.TraversalControl.QUIT;
import static java.lang.String.format;

/**
 * GraphQL Type visitors library
 */
class GraphqlTypeVisitors {

    private GraphqlTypeVisitors() {}


     static class LeafVisitor extends GraphqlTypeVisitorStub {

        @Override
        public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLType> context) {
            context.setResult(true);
            return QUIT;
        }

        @Override
        public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLType> context) {
            context.setResult(true);
            return QUIT;
        }

        @Override
        protected TraversalControl visitGraphQLType(GraphQLType node, TraverserContext<GraphQLType> context) {
            context.setResult(false);
            return QUIT;
        }

        @Override
        public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLType> context) {
            return node.getWrappedType().accept(context,this);
        }

        @Override
        public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLType> context) {
            return node.getWrappedType().accept(context,this);
        }

    }

    static class CollectingVisitor extends GraphqlTypeVisitorStub {

        private final Map<String,GraphQLType> result = new HashMap<>();

        public CollectingVisitor() {
        }

        @Override
        public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLType> context) {
            assertTypeUniqueness(node,result);
            save(node.getName(),node);
            return super.visitGraphQLEnumType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLType> context) {
            assertTypeUniqueness(node,result);
            save(node.getName(),node);
            return super.visitGraphQLScalarType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLType> context) {
            if (isTypeReference(node.getName())) {
                assertTypeUniqueness(node, result);
            } else {
                save(node.getName(), node);
            }
            return super.visitGraphQLObjectType(node,context);
        }

        @Override
        public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLType> context) {
            if (isTypeReference(node.getName())) {
                assertTypeUniqueness(node, result);
            } else {
                save(node.getName(), node);
            }
            return super.visitGraphQLInputObjectType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLType> context) {
            if (isTypeReference(node.getName())) {
                assertTypeUniqueness(node, result);
            } else {
                save(node.getName(), node);
            }

            return super.visitGraphQLInterfaceType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLType> context) {
            assertTypeUniqueness(node,result);
            save(node.getName(),node);
            return super.visitGraphQLUnionType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLType> context) {

            return super.visitGraphQLFieldDefinition(node, context);
        }

        // TODO: eh? Isn't it similar to assertTypeUniqueness?
        private boolean isTypeReference(String name) {
            return result.containsKey(name) && !(result.get(name) instanceof GraphQLTypeReference);
        }

        private void save(String name, GraphQLType type) {
            result.put(name,type);
        }


        /*
    From http://facebook.github.io/graphql/#sec-Type-System

       All types within a GraphQL schema must have unique names. No two provided types may have the same name.
       No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).

    Enforcing this helps avoid problems later down the track fo example https://github.com/graphql-java/graphql-java/issues/373
 */
        private void assertTypeUniqueness(GraphQLType type, Map<String, GraphQLType> result) {
            GraphQLType existingType = result.get(type.getName());
            // do we have an existing definition
            if (existingType != null) {
                // type references are ok
                if (!(existingType instanceof GraphQLTypeReference || type instanceof GraphQLTypeReference))
                    // object comparison here is deliberate
                    if (existingType != type) {
                        throw new AssertException(format("All types within a GraphQL schema must have unique names. No two provided types may have the same name.\n" +
                                        "No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).\n" +
                                        "You have redefined the type '%s' from being a '%s' to a '%s'",
                                type.getName(), existingType.getClass().getSimpleName(), type.getClass().getSimpleName()));
                    }
            }
        }

        public Map<String, GraphQLType> getResult() {
            return result;
        }


    }

    static class RefResolvingVisitor extends GraphqlTypeVisitorStub {

        final private GraphQLType resolvedType;
        public RefResolvingVisitor(GraphQLType resolvedType) {
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
            node.replaceType( resolvedType);
            return super.visitGraphQLList(node, context);
        }

        @Override
        public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLType> context) {
            node.replaceType( resolvedType);
            return super.visitGraphQLNonNull(node, context);
        }

    }

    static class TypeResolvingVisitor extends GraphqlTypeVisitorStub {

        private final Map<String, GraphQLType> typeMap;

        public TypeResolvingVisitor( Map<String, GraphQLType> typeMap) {
            this.typeMap = typeMap;
        }

        @Override
        public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLType> context) {

            node.replaceInterfaces(node.getInterfaces().stream()
                    .map(type -> (GraphQLOutputType)typeMap.get(type.getName()))
                    .collect(Collectors.toList()));
            return super.visitGraphQLObjectType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLType> context) {

            node.replaceTypes(node.getTypes().stream()
                    .map(type -> (GraphQLOutputType)typeMap.get(type.getName()))
                    .collect(Collectors.toList()));
            return super.visitGraphQLUnionType(node, context);
        }

        @Override
        public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLType> context) {

            final GraphQLType resolvedType = typeMap.get(node.getName());
            Assert.assertTrue(resolvedType != null, "type %s not found in schema", node.getName());
            context.getParentContext().thisNode().accept(context, new RefResolvingVisitor(resolvedType) );
            return super.visitGraphQLTypeReference(node, context);
        }
    }
}
