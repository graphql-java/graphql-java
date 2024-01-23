package example.http;

import graphql.incremental.DelayedIncrementalExecutionResult;
import graphql.incremental.IncrementalExecutionResult;
import jakarta.servlet.http.HttpServletResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Map;

public class DeferHttpSupport {

    private static final String CRLF = "\r\n";

    @SuppressWarnings("unchecked")
    static void sendIncrementalResponse(HttpServletResponse response, IncrementalExecutionResult incrementalExecutionResult) {

        Publisher<DelayedIncrementalExecutionResult> incrementalResults = incrementalExecutionResult.getIncrementalItemPublisher();

        try {
            sendMultipartResponse(response, incrementalExecutionResult, incrementalResults);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static private void sendMultipartResponse(
            HttpServletResponse response,
            IncrementalExecutionResult incrementalExecutionResult,
            Publisher<DelayedIncrementalExecutionResult> incrementalResults
    ) {
        // this implements this apollo defer spec: https://github.com/apollographql/apollo-server/blob/defer-support/docs/source/defer-support.md
        // the spec says CRLF + "-----" + CRLF is needed at the end, but it works without it and with it we get client
        // side errors with it, so we skp it
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Content-Type", "multipart/mixed; boundary=\"-\"");
        response.setHeader("Connection", "keep-alive");

        // send the first "un deferred" part of the result
        writeAndFlushPart(response, incrementalExecutionResult.toSpecification());

        // now send each deferred part which is given to us as a reactive stream
        // of deferred values
        incrementalResults.subscribe(new Subscriber<DelayedIncrementalExecutionResult>() {
            Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                s.request(1);
            }

            @Override
            public void onNext(DelayedIncrementalExecutionResult delayedIncrementalExecutionResult) {
                subscription.request(1);

                writeAndFlushPart(response, delayedIncrementalExecutionResult.toSpecification());
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
