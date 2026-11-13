package graphql.schema.impl;


import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.execution.MissingRootTypeException;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeResolvingVisitor;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.SchemaTraverser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;

@Internal
public class SchemaUtil {

    /**
     * Called to visit a partially built schema (during {@link GraphQLSchema} build phases) with a set of visitors
     *
     * Each visitor is expected to hold its own side effects that might be last used to construct a full schema
     *
     * @param partiallyBuiltSchema the partially built schema
     * @param visitors             the visitors to call
     */

    public static void visitPartiallySchema(final GraphQLSchema partiallyBuiltSchema, GraphQLTypeVisitor... visitors) {
        List<GraphQLSchemaElement> roots = new ArrayList<>();
        roots.add(partiallyBuiltSchema.getQueryType());

        if (partiallyBuiltSchema.isSupportingMutations()) {
            roots.add(partiallyBuiltSchema.getMutationType());
        }

        if (partiallyBuiltSchema.isSupportingSubscriptions()) {
            roots.add(partiallyBuiltSchema.getSubscriptionType());
        }

        if (partiallyBuiltSchema.getAdditionalTypes() != null) {
            roots.addAll(partiallyBuiltSchema.getAdditionalTypes());
        }

        if (partiallyBuiltSchema.getDirectives() != null) {
            roots.addAll(partiallyBuiltSchema.getDirectives());
        }

        roots.add(partiallyBuiltSchema.getIntrospectionSchemaType());

        GraphQLTypeVisitor visitor = new MultiReadOnlyGraphQLTypeVisitor(Arrays.asList(visitors));
        SchemaTraverser traverser;
        traverser = new SchemaTraverser(schemaElement -> schemaElement.getChildrenWithTypeReferences().getChildrenAsList());
        traverser.depthFirst(visitor, roots);
    }

    public static ImmutableMap<String, List<GraphQLObjectType>> groupInterfaceImplementationsByName(List<GraphQLNamedType> allTypesAsList) {
        Map<String, List<GraphQLObjectType>> result = new LinkedHashMap<>();
        for (GraphQLType type : allTypesAsList) {
            if (type instanceof GraphQLObjectType) {
                List<GraphQLNamedOutputType> interfaces = ((GraphQLObjectType) type).getInterfaces();
                for (GraphQLNamedOutputType interfaceType : interfaces) {
                    List<GraphQLObjectType> myGroup = result.computeIfAbsent(interfaceType.getName(), k -> new ArrayList<>());
                    myGroup.add((GraphQLObjectType) type);
                }
            }
        }
        return ImmutableMap.copyOf(new TreeMap<>(result));
    }

    public Map<String, List<GraphQLImplementingType>> groupImplementationsForInterfacesAndObjects(GraphQLSchema schema) {
        Map<String, List<GraphQLImplementingType>> result = new LinkedHashMap<>();
        for (GraphQLType type : schema.getAllTypesAsList()) {
            if (type instanceof GraphQLImplementingType) {
                List<GraphQLNamedOutputType> interfaces = ((GraphQLImplementingType) type).getInterfaces();
                for (GraphQLNamedOutputType interfaceType : interfaces) {
                    List<GraphQLImplementingType> myGroup = result.computeIfAbsent(interfaceType.getName(), k -> new ArrayList<>());
                    myGroup.add((GraphQLImplementingType) type);
                }
            }
        }
        return ImmutableMap.copyOf(new TreeMap<>(result));
    }

    public static void replaceTypeReferences(GraphQLSchema schema) {
        final Map<String, GraphQLNamedType> typeMap = schema.getTypeMap();
        List<GraphQLSchemaElement> roots = new ArrayList<>(typeMap.values());
        roots.addAll(schema.getDirectives());
        roots.addAll(schema.getSchemaAppliedDirectives());
        SchemaTraverser schemaTraverser = new SchemaTraverser(schemaElement -> schemaElement.getChildrenWithTypeReferences().getChildrenAsList());
        schemaTraverser.depthFirst(new GraphQLTypeResolvingVisitor(typeMap), roots);
    }

    public static GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
        OperationDefinition.Operation operation = operationDefinition.getOperation();
        if (operation == MUTATION) {
            GraphQLObjectType mutationType = graphQLSchema.getMutationType();
            if (mutationType == null) {
                throw new MissingRootTypeException("Schema is not configured for mutations.", operationDefinition.getSourceLocation());
            }
            return mutationType;
        } else if (operation == QUERY) {
            GraphQLObjectType queryType = graphQLSchema.getQueryType();
            if (queryType == null) {
                throw new MissingRootTypeException("Schema does not define the required query root type.", operationDefinition.getSourceLocation());
            }
            return queryType;
        } else if (operation == SUBSCRIPTION) {
            GraphQLObjectType subscriptionType = graphQLSchema.getSubscriptionType();
            if (subscriptionType == null) {
                throw new MissingRootTypeException("Schema is not configured for subscriptions.", operationDefinition.getSourceLocation());
            }
            return subscriptionType;
        } else {
            return assertShouldNeverHappen("Unhandled case. An extra operation enum has been added without code support");
        }
    }
}
