package graphql;


import java.util.List;

public interface ExecutionResult {

    <T> T getData();

    List<GraphQLError> getErrors();
}
