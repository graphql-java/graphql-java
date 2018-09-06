package graphql.execution.instrumentation.streaming;

import graphql.PublicSpi;

/**
 * This SPI will be called by {@link graphql.execution.instrumentation.streaming.StreamingJsonInstrumentation} to output
 * JSON values as they are encountered while a graphql query is executed.  They are modelled on the Jackson Streaming JSON
 * APIs however you can use any backing JSON implementation you choose.
 */
@PublicSpi
public interface JsonStream {

    /**
     * Start a JSON object
     */
    void writeStartObject();

    /**
     * Start a field array with the specified name
     *
     * @param fieldName the name of the JSON field
     */
    void writeArrayFieldStart(String fieldName);

    /**
     * Start a field object with the specified name
     *
     * @param fieldName the name of the JSON field
     */
    void writeObjectFieldStart(String fieldName);

    /**
     * End a JSON array
     */
    void writeEndArray();

    /**
     * End a JSON object
     */
    void writeEndObject();

    /**
     * This is called to write a leaf value (scalar or enum) to the JSON.  You will be given what ever
     * objects your scalar / enum graphql types have been returning and its up to your implementation
     * to turn them into valid JSON values.
     *
     * @param fieldName  the name of the JSON field
     * @param fieldValue the Java value to write
     */
    void writeJavaObject(String fieldName, Object fieldValue);

    /**
     * Called when the graphql query has finished and the underlying stream
     * of data can be flushed and closed
     */
    void finished();
}
