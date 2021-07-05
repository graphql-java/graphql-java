package readme;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;

import java.util.concurrent.ConcurrentMap;

import static graphql.StarWarsSchema.queryType;

@SuppressWarnings({"unused", "Convert2Lambda"})
public class ConcernsExamples {

    private GraphQLSchema schema = GraphQLSchema.newSchema()
            .query(queryType)
            .build();


    private GraphQL buildSchema() {
        return GraphQL.newGraphQL(schema)
                .build();
    }

    private static class User {

    }

    static class YourGraphqlContextBuilder {
        static UserContext getContextForUser(User user) {
            return null;
        }
    }

    private static class UserContext {

    }

    private User getCurrentUser() {
        return null;
    }


    private Object invokeBusinessLayerMethod(UserContext userCtx, Long businessObjId) {
        return null;
    }

    private GraphQL graphQL = GraphQL.newGraphQL(schema)
            .build();

    private void contextHelper() {
        //
        // this could be code that authorises the user in some way and sets up enough context
        // that can be used later inside data fetchers allowing them
        // to do their job
        //
        UserContext contextForUser = YourGraphqlContextBuilder.getContextForUser(getCurrentUser());

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .graphQLContext(context -> context.put("userContext", contextForUser))
                .build();

        ExecutionResult executionResult = graphQL.execute(executionInput);

        // ...
        //
        // later you are able to use this context object when a data fetcher is invoked
        //

        DataFetcher dataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                UserContext userCtx = environment.getGraphQlContext().get("userContext");
                Long businessObjId = environment.getArgument("businessObjId");

                return invokeBusinessLayerMethod(userCtx, businessObjId);
            }
        };
    }


}
