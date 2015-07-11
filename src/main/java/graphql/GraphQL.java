package graphql;


import graphql.execution.Execution;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphQL {


    private final GraphQLSchema graphQLSchema;
    private final String requestString;
    private final Map<String, Object> arguments = new LinkedHashMap<>();

    public GraphQL(GraphQLSchema graphQLSchema, String requestString) {
        this(graphQLSchema, requestString, Collections.<String, Object>emptyMap());
    }

    public GraphQL(GraphQLSchema graphQLSchema, String requestString, Map<String, Object> arguments) {
        this.graphQLSchema = graphQLSchema;
        this.requestString = requestString;
        this.arguments.putAll(arguments);
    }

    public Object execute() {
        Parser parser = new Parser();
        Document document = parser.parseDocument(requestString);
        Execution execution = new Execution();
        return execution.execute(graphQLSchema, null, document, null, arguments).getResut();
    }


}
