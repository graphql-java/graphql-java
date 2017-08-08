package graphql.schema;

import java.util.List;


/**
 * Types that can contain output fields are marked with this interface
 */
public interface GraphQLFieldsContainer extends GraphQLType {

    GraphQLFieldDefinition getFieldDefinition(String name);

    List<GraphQLFieldDefinition> getFieldDefinitions();
}
