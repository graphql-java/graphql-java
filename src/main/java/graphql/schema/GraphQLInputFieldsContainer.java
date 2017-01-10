package graphql.schema;

import java.util.List;

public interface GraphQLInputFieldsContainer extends GraphQLType {

	GraphQLInputObjectField getFieldDefinition(String name);

	List<GraphQLInputObjectField> getFieldDefinitions();
}