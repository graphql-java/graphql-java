package graphql;


import java.util.List;

public interface ExecutionResult {

    Object getData();

    List<GraphQLError> getErrors();
}
