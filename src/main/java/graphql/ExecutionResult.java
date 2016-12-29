package graphql;


import java.util.List;
import java.util.Map;

public interface ExecutionResult {

    <T> T getData();

    List<GraphQLError> getErrors();

    Map<Object,Object> getExtensions();

}
