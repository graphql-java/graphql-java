package graphql.schema.validation.rules;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;

public interface SchemaValidationRule {

    void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector);

}
