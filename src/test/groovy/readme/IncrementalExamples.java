package readme;

import graphql.Directives;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.incremental.DelayedIncrementalPartialResult;
import graphql.incremental.IncrementalExecutionResult;
import graphql.schema.GraphQLSchema;
import jakarta.servlet.http.HttpServletResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@SuppressWarnings({"unused", "ConstantConditions"})
public class IncrementalExamples {

    GraphQLSchema buildSchemaWithDirective() {

        GraphQLSchema schema = buildSchema();
        schema = schema.transform(builder ->
                builder.additionalDirective(Directives.DeferDirective)
        );
        return schema;
    }

    void basicExample(HttpServletResponse httpServletResponse, String deferredQuery) {
        GraphQLSchema schema = buildSchemaWithDirective();
        GraphQL graphQL = GraphQL.newGraphQL(schema).build();

        //
        // deferredQuery contains the query with @defer directives in it
        //
        ExecutionResult initialResult = graphQL.execute(ExecutionInput.newExecutionInput().query(deferredQuery).build());

        if (!(initialResult instanceof IncrementalExecutionResult)) {
            // handle non incremental response
            return;
        }

        IncrementalExecutionResult incrementalResult = (IncrementalExecutionResult) initialResult;

        //
        // then initial results happen first, the incremental ones will begin AFTER these initial
        // results have completed
        //
        sendMultipartHttpResult(httpServletResponse, initialResult);

        Publisher<DelayedIncrementalPartialResult> delayedIncrementalResults = incrementalResult
                .getIncrementalItemPublisher();

        //
        // you subscribe to the incremental results like any other reactive stream
        //
        delayedIncrementalResults.subscribe(new Subscriber<>() {

            Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                //
                // how many you request is up to you
                subscription.request(10);
            }

            @Override
            public void onNext(DelayedIncrementalPartialResult executionResult) {
                //
                // as each deferred result arrives, send it to where it needs to go
                //
                sendMultipartHttpResult(httpServletResponse, executionResult);
                subscription.request(10);
            }

            @Override
            public void onError(Throwable t) {
                handleError(httpServletResponse, t);
            }

            @Override
            public void onComplete() {
                completeResponse(httpServletResponse);
            }
        });
    }

    private void completeResponse(HttpServletResponse httpServletResponse) {
    }

    private void handleError(HttpServletResponse httpServletResponse, Throwable t) {
    }

    private void sendMultipartHttpResult(HttpServletResponse httpServletResponse, Object result) {
    }


    private GraphQLSchema buildSchema() {
        return null;
    }

}
