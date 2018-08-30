package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Field;
import graphql.language.SelectionSetContainer;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;

import java.util.Map;

@PublicApi
public interface QueryVisitorFieldEnvironment {

    /**
     * @return true if the current field is __typename
     */
    boolean isTypeNameIntrospectionField();

    /**
     * @return the current Field
     */
    Field getField();

    GraphQLFieldDefinition getFieldDefinition();

    /**
     * @return the parent output type of the current field.
     */
    GraphQLOutputType getParentType();

    /**
     * @return the unmodified fields container fot the current type
     *
     * @throws IllegalStateException if the current field is __typename see {@link #isTypeNameIntrospectionField()}
     */
    GraphQLFieldsContainer getFieldsContainer();

    QueryVisitorFieldEnvironment getParentEnvironment();

    Map<String, Object> getArguments();

    SelectionSetContainer getSelectionSetContainer();
}
