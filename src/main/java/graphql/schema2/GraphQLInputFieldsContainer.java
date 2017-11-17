package graphql.schema2;

import graphql.PublicApi;
import graphql.schema.GraphQLType;

import java.util.List;

/**
 * Types that can contain input fields are marked with this interface
 *
 * @see graphql.schema.GraphQLInputType
 */
@PublicApi
public interface GraphQLInputFieldsContainer extends GraphQLType {

    graphql.schema2.GraphQLInputObjectField getFieldDefinition(String name);

    List<graphql.schema2.GraphQLInputObjectField> getFieldDefinitions();
}