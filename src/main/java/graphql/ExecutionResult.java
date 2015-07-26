package graphql;


import java.util.List;
import java.util.Map;

public interface ExecutionResult {

    Map<String, Object> getData();

    List<GraphQLError> getErrors();
}
