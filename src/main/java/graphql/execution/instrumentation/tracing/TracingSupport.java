package graphql.execution.instrumentation.tracing;

import graphql.PublicApi;
import graphql.execution.ExecutionTypeInfo;
import graphql.schema.DataFetchingEnvironment;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This creates a map of tracing information as outlined in https://github.com/apollographql/apollo-tracing
 * <p>
 * This is a stateful object that should be instantiated and called via {@link java.lang.instrument.Instrumentation}
 * calls.  It has been made a separate class so that you can compose this into existing
 * instrumentation code.
 */
@PublicApi
public class TracingSupport {

    private final ZonedDateTime startTime;
    private final long startNanos;
    private final ConcurrentLinkedQueue<Map<String, Object>> fieldData;

    /**
     * The timer starts as soon as you create this object
     */
    public TracingSupport() {
        startNanos = System.nanoTime();
        startTime = ZonedDateTime.now();
        fieldData = new ConcurrentLinkedQueue<>();
    }

    /**
     * A simple object that you need to call {@link #onEnd()} on
     */
    public interface TracingContext {
        /**
         * Call this to end the current trace context
         */
        void onEnd();
    }

    /**
     * This should be called to start the trace of a field, with {@link TracingContext#onEnd()} being called to
     * end the call.
     *
     * @param dataFetchingEnvironment the data fetching that is occurring
     *
     * @return a context to call end on
     */
    public TracingContext beginField(DataFetchingEnvironment dataFetchingEnvironment) {
        long startFieldFetch = System.nanoTime();
        return () -> {
            long now = System.nanoTime();
            long duration = now - startFieldFetch;
            long startOffset = now - this.startNanos;
            ExecutionTypeInfo typeInfo = dataFetchingEnvironment.getFieldTypeInfo();

            Map<String, Object> fetchMap = new LinkedHashMap<>();
            fetchMap.put("path", typeInfo.getPath().toList());
            fetchMap.put("parentType", typeInfo.getParentTypeInfo().toAst());
            fetchMap.put("returnType", typeInfo.toAst());
            fetchMap.put("fieldName", typeInfo.getFieldDefinition().getName());
            fetchMap.put("startOffset", startOffset);
            fetchMap.put("duration", duration);

            fieldData.add(fetchMap);
        };
    }

    /**
     * This will snapshot this tracing and return a map of the results
     *
     * @return a snapshot of the tracing data
     */
    public Map<String, Object> snapshotTracingData() {

        Map<String, Object> traceMap = new LinkedHashMap<>();
        traceMap.put("version", 1L);
        traceMap.put("startTime", rfc3339(startTime));
        traceMap.put("endTime", rfc3339(ZonedDateTime.now()));
        traceMap.put("duration", System.nanoTime() - startNanos);
        traceMap.put("execution", executionData());

        return traceMap;
    }

    private Map<String, Object> executionData() {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>(fieldData);
        map.put("resolvers", list);
        return map;
    }

    private String rfc3339(ZonedDateTime time) {
        return time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

}
