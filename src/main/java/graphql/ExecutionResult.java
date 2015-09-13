package graphql;


import java.util.List;
import java.util.Map;

public interface ExecutionResult {

    Object getData();

    List<GraphQLError> getErrors();
}
