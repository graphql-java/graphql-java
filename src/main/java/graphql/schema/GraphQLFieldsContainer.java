package graphql.schema;

import java.util.List;


public interface GraphQLFieldsContainer {

    GraphQLFieldDefinition getFieldDefinition(String name);

    List<GraphQLFieldDefinition> getFieldDefinitions();
}
