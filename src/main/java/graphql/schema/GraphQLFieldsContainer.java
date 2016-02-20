package graphql.schema;

import java.util.List;


public interface GraphQLFieldsContainer extends GraphQLType {

    GraphQLFieldDefinition getFieldDefinition(String name);

    List<GraphQLFieldDefinition> getFieldDefinitions();
}
