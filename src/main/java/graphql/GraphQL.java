package graphql;


import graphql.execution.Execution;
import graphql.execution.ExecutionResult;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GraphQL {


    private final GraphQLSchema graphQLSchema;
    private final String requestString;
    private final Map<String, Object> arguments = new LinkedHashMap<>();
    private final ExecutorService executorService;

    public GraphQL(GraphQLSchema graphQLSchema, String requestString) {
        this(graphQLSchema, requestString, Collections.<String, Object>emptyMap());
    }

    public GraphQL(GraphQLSchema graphQLSchema, String requestString, Map<String, Object> arguments) {
        this(graphQLSchema, requestString, arguments, Executors.newCachedThreadPool());
    }

    public GraphQL(GraphQLSchema graphQLSchema, String requestString, Map<String, Object> arguments, ExecutorService executorService) {
        this.graphQLSchema = graphQLSchema;
        this.requestString = requestString;
        this.arguments.putAll(arguments);
        this.executorService = executorService;
    }

    public ExecutionResult execute() {
        Parser parser = new Parser();
        Document document = parser.parseDocument(requestString);
        Execution execution = new Execution(executorService);
        Validator validator = new Validator();
        List<ValidationError> validationErrors = validator.validateDocument(graphQLSchema, document);
        if (validationErrors.size() > 0) {
            ExecutionResult result = new ExecutionResult(validationErrors);
            return result;
        }
        return execution.execute(graphQLSchema, null, document, null, arguments);
    }


}
