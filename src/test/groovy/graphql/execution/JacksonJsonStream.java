package graphql.execution;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.execution.streaming.JsonStream;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

public class JacksonJsonStream implements JsonStream {

    private final JsonGenerator jGenerator;

    private static void toUncheckedIOException(IOException e) {
        throw new UncheckedIOException(e);
    }

    public JacksonJsonStream(PrintStream out) {
        jGenerator = mkJsonGenerator(out);
    }

    static JsonGenerator mkJsonGenerator(PrintStream out) {
        JsonGenerator jGenerator = null;
        try {
            JsonFactory jsonFactory = new JsonFactory();
            jGenerator = jsonFactory.createGenerator(out, JsonEncoding.UTF8);
            jGenerator.setCodec(new ObjectMapper());
            jGenerator.useDefaultPrettyPrinter();
        } catch (IOException e) {
            toUncheckedIOException(e);
        }
        return jGenerator;
    }

    @Override
    public void writeStartObject() {
        try {
            jGenerator.writeStartObject();
            jGenerator.flush();
        } catch (IOException e) {
            toUncheckedIOException(e);
        }
    }

    @Override
    public void writeArrayFieldStart(String fieldName) {
        try {
            jGenerator.writeArrayFieldStart(fieldName);
            jGenerator.flush();
        } catch (IOException e) {
            toUncheckedIOException(e);
        }
    }

    @Override
    public void writeObjectFieldStart(String fieldName) {
        try {
            jGenerator.writeObjectFieldStart(fieldName);
            jGenerator.flush();
        } catch (IOException e) {
            toUncheckedIOException(e);
        }
    }

    @Override
    public void writeEndArray() {
        try {
            jGenerator.writeEndArray();
            jGenerator.flush();
        } catch (IOException e) {
            toUncheckedIOException(e);
        }
    }

    @Override
    public void writeEndObject() {
        try {
            jGenerator.writeEndObject();
            jGenerator.flush();
        } catch (IOException e) {
            toUncheckedIOException(e);
        }
    }

    @Override
    public void writeJavaObject(String fieldName, Object fieldValue) {
        try {
            jGenerator.writeObjectField(fieldName, fieldValue);
            jGenerator.flush();
        } catch (IOException e) {
            toUncheckedIOException(e);
        }
    }

    @Override
    public void finished() {
        try {
            jGenerator.flush();
            jGenerator.flush();
        } catch (IOException e) {
            toUncheckedIOException(e);
        }
    }
}
