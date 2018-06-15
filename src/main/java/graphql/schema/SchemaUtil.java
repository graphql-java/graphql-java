package graphql.schema;


import graphql.Assert;
import graphql.AssertException;
import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.util.TraversalControl;
import static graphql.util.TraversalControl.QUIT;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static java.lang.String.format;

@Internal
public class SchemaUtil {

    public Boolean isLeafType(GraphQLType graphQLType) {
        return (Boolean) SHALLOW_TRAVERSER.depthFirst(new LeafVisitor(), graphQLType).getResult();
    }

    public boolean isInputType(GraphQLType graphQLType) {
        return (Boolean) SHALLOW_TRAVERSER.depthFirst(new LeafVisitor() {
            @Override
            public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLType> context) {
                context.setResult(true);
                return QUIT;
            }
        }, graphQLType).getResult();
    }


    /**
     * Use custom visitor
     */
    @Deprecated
    public GraphQLUnmodifiedType getUnmodifiedType(GraphQLType graphQLType) {
        if (graphQLType instanceof GraphQLModifiedType) {
            return getUnmodifiedType(((GraphQLModifiedType) graphQLType).getWrappedType());
        }
        return (GraphQLUnmodifiedType) graphQLType;

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

    Map<String, GraphQLType> allTypes(final GraphQLSchema schema,final Set<GraphQLType> additionalTypes) {
        List<GraphQLType> roots = new ArrayList<GraphQLType>() {{
            add(schema.getQueryType());

            if(schema.isSupportingMutations()) {
                add(schema.getMutationType());
            }

            if(schema.isSupportingSubscriptions()) {
                add(schema.getSubscriptionType());
            }

            if(additionalTypes != null) {
                addAll(additionalTypes);
            }

            add(Introspection.__Schema);
        }};

        CollectingVisitor visitor = new CollectingVisitor();
        TRAVERSER.depthFirst(visitor, roots);
        return visitor.getResult();
    }



    /*
     * Indexes GraphQLObject types registered with the provided schema by implemented GraphQLInterface name
     *
     * This helps in accelerates/simplifies collecting types that implement a certain interface
     *
     * Provided to replace {@link #findImplementations(graphql.schema.GraphQLSchema, graphql.schema.GraphQLInterfaceType)}
     * 
     */
    Map<String, List<GraphQLObjectType>> groupImplementations(GraphQLSchema schema) {
        Map<String, List<GraphQLObjectType>> result = new HashMap<>();
        for (GraphQLType type : schema.getAllTypesAsList()) {
            if (type instanceof GraphQLObjectType) {
                for (GraphQLOutputType interfaceType : ((GraphQLObjectType) type).getInterfaces()) {
                    List<GraphQLObjectType> myGroup = result.computeIfAbsent(interfaceType.getName(), k -> new ArrayList<>());
                    myGroup.add((GraphQLObjectType) type);
                }
            }
        }

        return result;
    }

    /**
     * This method is deprecated due to a performance concern.
     *
     * The Algorithm complexity: O(n^2), where n is number of registered GraphQLTypes
     *
     * That indexing operation is performed twice per input document:
     * 1. during validation
     * 2. during execution
     *
     * We now indexed all types at the schema creation, which has brought complexity down to O(1)
     *
     * @param schema        GraphQL schema
     * @param interfaceType an interface type to find implementations for
     *
     * @return List of object types implementing provided interface
     *
     * @deprecated use {@link graphql.schema.GraphQLSchema#getImplementations(GraphQLInterfaceType)} instead
     */
    @Deprecated
    public List<GraphQLObjectType> findImplementations(GraphQLSchema schema, GraphQLInterfaceType interfaceType) {
        List<GraphQLObjectType> result = new ArrayList<>();
        for (GraphQLType type : schema.getAllTypesAsList()) {
            if (!(type instanceof GraphQLObjectType)) {
                continue;
            }
            GraphQLObjectType objectType = (GraphQLObjectType) type;
            if ((objectType).getInterfaces().contains(interfaceType)) {
                result.add(objectType);
            }
        }
        return result;
    }

    void replaceTypeReferences(GraphQLSchema schema) {

        final Map<String, GraphQLType> typeMap = schema.getTypeMap();

        TRAVERSER.depthFirst(new TypeVisitorStub() {

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
        },typeMap.values());

    }

    @Deprecated
    GraphQLType resolveTypeReference(GraphQLType type, Map<String, GraphQLType> typeMap) {
        if (type instanceof GraphQLTypeReference || typeMap.containsKey(type.getName())) {
            GraphQLType resolvedType = typeMap.get(type.getName());
            Assert.assertTrue(resolvedType != null, "type %s not found in schema", type.getName());
            return resolvedType;
        }
        return type;
    }


    class RefResolvingVisitor extends TypeVisitorStub {

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


    public static class LeafVisitor extends TypeVisitorStub {

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


    class CollectingVisitor extends TypeVisitorStub {

        private final Map<String,GraphQLType> result = new HashMap<>();

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

        public Map<String, GraphQLType> getResult() {
            return result;
        }
    }



    private static final TypeTraverser SHALLOW_TRAVERSER = new TypeTraverser((node) -> Collections.emptyList());

    private static final TypeTraverser TRAVERSER = new TypeTraverser();



}
