package graphql.schema;

import graphql.PublicApi;

import java.util.List;

/**
 * This allows you to control the visibility of graphql fields and enum values.  By default
 * graphql-java makes every defined field visible but you can implement an instance of this
 * interface and reduce specific field visibility.
 */
@PublicApi
public interface GraphqlFieldVisibility {

    /**
     * Called to get the list of fields from an object type or interface
     *
     * @param fieldsContainer the type in play
     *
     * @return a non null list of {@link graphql.schema.GraphQLFieldDefinition}s
     */
    List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer);

    /**
     * Called to get a named field from an object type or interface
     *
     * @param fieldsContainer the type in play
     * @param fieldName       the name of the desired field
     *
     * @return a {@link graphql.schema.GraphQLFieldDefinition} or null if its not visible
     */
    GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName);

    /**
     * Called to get the list of enum values of an enum type
     *
     * @param enumType the enum type in play
     *
     * @return a non null list of {@link graphql.schema.GraphQLEnumValueDefinition}s
     */
    List<GraphQLEnumValueDefinition> getValues(GraphQLEnumType enumType);

    /**
     * Called to get a named enum value from an enum type
     *
     * @param enumType the enum type in play
     * @param enumName the name of the enum value
     *
     * @return a {@link graphql.schema.GraphQLEnumValueDefinition} or null if its not visible
     */
    GraphQLEnumValueDefinition getValue(GraphQLEnumType enumType, String enumName);


    /**
     * A field visibility that shows all fields
     */
    GraphqlFieldVisibility DEFAULT_VISIBILITY = new GraphqlFieldVisibility() {
        @Override
        public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
            return fieldsContainer.getFieldDefinitions();
        }

        @Override
        public GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
            return fieldsContainer.getFieldDefinition(fieldName);
        }

        @Override
        public List<GraphQLEnumValueDefinition> getValues(GraphQLEnumType type) {
            return type.getValues();
        }

        @Override
        public GraphQLEnumValueDefinition getValue(GraphQLEnumType type, String enumName) {
            return type.getValue(enumName);
        }
    };


}
