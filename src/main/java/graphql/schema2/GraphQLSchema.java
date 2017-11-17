package graphql.schema2;

import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final GraphQLObjectType subscriptionType;
    private final Map<String, GraphQLType> types = new LinkedHashMap<>();
    private final Set<GraphQLType> additionalTypes;
    private final Set<GraphQLDirective> directives;
    private final GraphqlFieldVisibility fieldVisibility;

    public GraphQLSchema(GraphQLObjectType queryType, GraphQLObjectType mutationType, GraphQLObjectType subscriptionType, Set<GraphQLType> additionalTypes, Set<GraphQLDirective> directives, GraphqlFieldVisibility fieldVisibility) {
        this.queryType = queryType;
        this.mutationType = mutationType;
        this.subscriptionType = subscriptionType;
        this.additionalTypes = additionalTypes;
        this.directives = directives;
        this.fieldVisibility = fieldVisibility;
    }

    public GraphQLType resolveTypeReference(TypeReference typeReference) {
        return types.get(typeReference.getName());
    }
}
