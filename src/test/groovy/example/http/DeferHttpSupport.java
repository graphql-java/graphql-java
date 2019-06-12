package example.http;

import graphql.DeferredExecutionResult;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Map;

public class DeferHttpSupport {

    private static final String CRLF = "\r\n";

    @SuppressWarnings("unchecked")
    static void sendDeferredResponse(HttpServletResponse response, ExecutionResult executionResult, Map<Object, Object> extensions) {
        Publisher<DeferredExecutionResult> deferredResults = (Publisher<DeferredExecutionResult>) extensions.get(GraphQL.DEFERRED_RESULTS);
        try {
            sendMultipartResponse(response, executionResult, deferredResults);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static private void sendMultipartResponse(HttpServletResponse response, ExecutionResult executionResult, Publisher<DeferredExecutionResult> deferredResults) {
        // this implements this apollo defer spec: https://github.com/apollographql/apollo-server/blob/defer-support/docs/source/defer-support.md
        // the spec says CRLF + "-----" + CRLF is needed at the end, but it works without it and with it we get client
        // side errors with it, so we skp it
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Content-Type", "multipart/mixed; boundary=\"-\"");
        response.setHeader("Connection", "keep-alive");

        // send the first "un deferred" part of the result
        writeAndFlushPart(response, executionResult.toSpecification());

        // now send each deferred part which is given to us as a reactive stream
        // of deferred values
        deferredResults.subscribe(new Subscriber<DeferredExecutionResult>() {
            Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(1);
            }

            @Override
            public void onNext(DeferredExecutionResult deferredExecutionResult) {
                subscription.request(1);

                writeAndFlushPart(response, deferredExecutionResult.toSpecification());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace(System.err);
            }

            @Override
            public void onComplete() {
            }
        });

    }

    private static void writeAndFlushPart(HttpServletResponse response, Map<String, Object> result) {
        DeferMultiPart deferMultiPart = new DeferMultiPart(result);
        StringBuilder sb = new StringBuilder();
        sb.append(CRLF).append("---").append(CRLF);
        String body = deferMultiPart.write();
        sb.append(body);
        writeAndFlush(response, sb);
    }

    private static void writeAndFlush(HttpServletResponse response, StringBuilder sb) {
        try {
            PrintWriter writer = response.getWriter();
            writer.write(sb.toString());
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private static class DeferMultiPart {

        private Object body;

        public DeferMultiPart(Object data) {
            this.body = data;
        }

        public String write() {
            StringBuilder result = new StringBuilder();
            String bodyString = bodyToString();
            result.append("Content-Type: application/json").append(CRLF);
            result.append("Content-Length: ").append(bodyString.length()).append(CRLF).append(CRLF);
            result.append(bodyString);
            return result.toString();
        }

        private String bodyToString() {
            return JsonKit.GSON.toJson(body);
        }
    }

}
