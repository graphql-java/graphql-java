package graphql.schema.diffing;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLType;

public interface SchemaChangedHandler {

    void fieldRemoved(String description);

}
