package graphql;

import java.util.Map;

@PublicApi
public interface CustomGraphQLError extends GraphQLError{
    Map<String,?> getCustomData(); 
}
