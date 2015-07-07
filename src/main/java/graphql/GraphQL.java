package graphql;


import graphql.execution.Execution;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;

public class GraphQL {


    private GraphQLSchema graphQLSchema;
    private String requestString;

    public GraphQL(GraphQLSchema graphQLSchema, String requestString) {
        this.graphQLSchema = graphQLSchema;
        this.requestString = requestString;
    }

    public Object execute() {
        Parser parser = new Parser();
        Document document = parser.parseDocument(requestString);
        Execution execution = new Execution();
        return execution.execute(graphQLSchema, null, document, null, null).getResut();
    }


}
