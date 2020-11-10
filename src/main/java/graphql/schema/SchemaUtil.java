package graphql.schema;


import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.introspection.Introspection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Internal
public class SchemaUtil {

    private static final SchemaTraverser TRAVERSER = new SchemaTraverser();


    ImmutableMap<String, GraphQLNamedType> allTypes(final GraphQLSchema schema, final Set<GraphQLType> additionalTypes, boolean afterTransform) {
        List<GraphQLSchemaElement> roots = new ArrayList<>();
        roots.add(schema.getQueryType());

        if (schema.isSupportingMutations()) {
            roots.add(schema.getMutationType());
        }

        if (schema.isSupportingSubscriptions()) {
            roots.add(schema.getSubscriptionType());
        }

        if (additionalTypes != null) {
            roots.addAll(additionalTypes);
        }

        if (schema.getDirectives() != null) {
            roots.addAll(schema.getDirectives());
        }

        roots.add(Introspection.__Schema);

        GraphQLTypeCollectingVisitor visitor = new GraphQLTypeCollectingVisitor();
        SchemaTraverser traverser;
        if (afterTransform) {
            traverser = new SchemaTraverser(schemaElement -> schemaElement.getChildrenWithTypeReferences().getChildrenAsList());
        } else {
            traverser = new SchemaTraverser();
        }
        traverser.depthFirst(visitor, roots);
        Map<String, GraphQLNamedType> result = visitor.getResult();
        return ImmutableMap.copyOf(new TreeMap<>(result));
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
        Map<String, List<GraphQLObjectType>> result = new LinkedHashMap<>();
        for (GraphQLType type : schema.getAllTypesAsList()) {
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
        final Map<String, GraphQLNamedType> typeMap = schema.getTypeMap();
        List<GraphQLSchemaElement> roots = new ArrayList<>(typeMap.values());
        roots.addAll(schema.getDirectives());
        SchemaTraverser schemaTraverser = new SchemaTraverser(schemaElement -> schemaElement.getChildrenWithTypeReferences().getChildrenAsList());
        schemaTraverser.depthFirst(new GraphQLTypeResolvingVisitor(typeMap), roots);
    }

    void extractCodeFromTypes(GraphQLCodeRegistry.Builder codeRegistry, GraphQLSchema schema) {
        Introspection.addCodeForIntrospectionTypes(codeRegistry);

        TRAVERSER.depthFirst(new CodeRegistryVisitor(codeRegistry), schema.getAllTypesAsList());
    }
}
