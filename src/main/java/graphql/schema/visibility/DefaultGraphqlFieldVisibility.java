package graphql.schema.visibility;

import graphql.PublicApi;
import graphql.schema.GraphQLFieldDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputFieldsContainer;
import graphql.schema.GraphQLInputObjectField;

import java.util.List;

/**
 * The default field visibility of graphql-java is that everything is visible
 */
@PublicApi
@NullMarked
public class DefaultGraphqlFieldVisibility implements GraphqlFieldVisibility {

    public static final DefaultGraphqlFieldVisibility DEFAULT_FIELD_VISIBILITY = new DefaultGraphqlFieldVisibility();

    @Override
    public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
        return fieldsContainer.getFieldDefinitions();
    }

    @Override
    public @Nullable GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
        return fieldsContainer.getFieldDefinition(fieldName);
    }

    @Override
    public List<GraphQLInputObjectField> getFieldDefinitions(GraphQLInputFieldsContainer fieldsContainer) {
        return fieldsContainer.getFieldDefinitions();
    }

    @Override
    public @Nullable GraphQLInputObjectField getFieldDefinition(GraphQLInputFieldsContainer fieldsContainer, String fieldName) {
        return fieldsContainer.getFieldDefinition(fieldName);
    }
}