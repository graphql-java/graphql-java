package graphql;


import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

public class Execution {

    public Execution(Document document, GraphQLSchema graphQLSchema) {

    }


    private void executeOperation(OperationDefinition operationDefinition) {

    }

    private GraphQLObjectType getOperationRootType(OperationDefinition operationDefinition, GraphQLSchema graphQLSchema) {
        return null;
    }
}
