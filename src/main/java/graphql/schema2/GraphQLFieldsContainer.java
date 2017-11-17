package graphql.schema2;

import graphql.schema.GraphQLType;

import java.util.List;


/**
 * Types that can contain output fields are marked with this interface
 *
 * @see graphql.schema.GraphQLObjectType
 * @see graphql.schema.GraphQLInterfaceType
 */
public interface GraphQLFieldsContainer extends GraphQLType {

    graphql.schema2.GraphQLFieldDefinition getFieldDefinition(String name);

    List<graphql.schema2.GraphQLFieldDefinition> getFieldDefinitions();
}
