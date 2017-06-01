package graphql;


import java.util.List;
import java.util.Map;

@PublicApi
public interface ExecutionResult {

    <T> T getData();

    List<GraphQLError> getErrors();

    Map<Object, Object> getExtensions();

    /**
     * Makes the ExecutionResult ready to be serialized according to the GraphQL specifications
     * (Call this method just before you make ExecutionResult a JSON object)
     * @return ExecutionResult that can be serialized according to the GraphQL specifications
     */
    ExecutionResult toSpecification();

}
