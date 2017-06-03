package graphql;


import java.util.List;

@PublicApi
public interface ExecutionResult {

    <T> T getData();

    List<GraphQLError> getErrors();

}
