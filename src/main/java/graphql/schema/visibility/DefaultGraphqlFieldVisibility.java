package graphql.schema.visibility;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;

import java.util.List;

/**
 * The default field visibility of graphql-java is that everything is visible
 */
public class DefaultGraphqlFieldVisibility implements GraphqlFieldVisibility {

    public static final DefaultGraphqlFieldVisibility DEFAULT_FIELD_VISIBILITY = new DefaultGraphqlFieldVisibility();

    @Override
    public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer, GraphqlFieldVisibilityEnvironment graphqlFieldVisibilityEnvironment) {
        return fieldsContainer.getFieldDefinitions();
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName, GraphqlFieldVisibilityEnvironment graphqlFieldVisibilityEnvironment) {
        return fieldsContainer.getFieldDefinition(fieldName);
    }
}
