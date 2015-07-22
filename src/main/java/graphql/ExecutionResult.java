package graphql;


import java.util.List;
import java.util.Map;

public interface ExecutionResult {

    Map<String, Object> getResult();

    List<GraphQLError> getErrors();
}
