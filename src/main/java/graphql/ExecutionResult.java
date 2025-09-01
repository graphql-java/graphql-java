package graphql;


import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This simple value class represents the result of performing a graphql query.
 */
@PublicApi
@NullMarked
@SuppressWarnings("TypeParameterUnusedInFormals")
public interface ExecutionResult {

    /**
     * @return the errors that occurred during execution or empty list if there is none
     */
    List<GraphQLError> getErrors();

    /**
     * @param <T> allows type coercion
     *
     * @return the data in the result or null if there is none
     */
    <T> @Nullable T getData();

    /**
     * The graphql specification specifies:
     * <p>
     * "If an error was encountered before execution begins, the data entry should not be present in the result.
     * If an error was encountered during the execution that prevented a valid response, the data entry in the response should be null."
     * <p>
     * This allows to distinguish between the cases where {@link #getData()} returns null.
     * <p>
     * See : <a href="https://graphql.github.io/graphql-spec/June2018/#sec-Data">https://graphql.github.io/graphql-spec/June2018/#sec-Data</a>
     *
     * @return <code>true</code> if the entry "data" should be present in the result
     * <code>false</code> otherwise
     */
    boolean isDataPresent();

    /**
     * @return a map of extensions or null if there are none
     */
    @Nullable Map<Object, Object> getExtensions();


    /**
     * The graphql specification says that result of a call should be a map that follows certain rules on what items
     * should be present.  Certain JSON serializers may or may interpret {@link ExecutionResult} to spec, so this method
     * is provided to produce a map that strictly follows the specification.
     * <p>
     * See : <a href="https://spec.graphql.org/October2021/#sec-Response-Format">https://spec.graphql.org/October2021/#sec-Response-Format</a>
     *
     * @return a map of the result that strictly follows the spec
     */
    Map<String, Object> toSpecification();

    /**
     * This allows you to turn a map of results from {@link #toSpecification()} and turn it back into a {@link ExecutionResult}
     *
     * @param specificationMap the specification result map
     *
     * @return a new {@link ExecutionResult} from that map
     */
    static ExecutionResult fromSpecification(Map<String, Object> specificationMap) {
        return ExecutionResultImpl.fromSpecification(specificationMap);
    }

    /**
     * This helps you transform the current {@link ExecutionResult} object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new {@link ExecutionResult} object based on calling build on that builder
     */
    default ExecutionResult transform(Consumer<Builder<?>> builderConsumer) {
        Builder<?> builder = newExecutionResult().from(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * @return a builder that allows you to build a new execution result
     */
    static Builder<?> newExecutionResult() {
        return ExecutionResultImpl.newExecutionResult();
    }

    @NullUnmarked
    interface Builder<B extends Builder<B>> {

        /**
         * Sets values into the builder based on a previous {@link ExecutionResult}
         *
         * @param executionResult the previous {@link ExecutionResult}
         *
         * @return the builder
         */
        B from(ExecutionResult executionResult);

        /**
         * Sets new data into the builder
         *
         * @param data the data to use
         *
         * @return the builder
         */
        B data(Object data);

        /**
         * Sets error list as the errors for this builder
         *
         * @param errors the errors to use
         *
         * @return the builder
         */
        B errors(List<GraphQLError> errors);

        /**
         * Adds the error list to any existing the errors for this builder
         *
         * @param errors the errors to add
         *
         * @return the builder
         */
        B addErrors(List<GraphQLError> errors);

        /**
         * Adds the error to any existing the errors for this builder
         *
         * @param error the error to add
         *
         * @return the builder
         */
        B addError(GraphQLError error);

        /**
         * Sets the extension map for this builder
         *
         * @param extensions the extensions to use
         *
         * @return the builder
         */
        B extensions(Map<Object, Object> extensions);

        /**
         * Adds a new entry into the extensions map for this builder
         *
         * @param key   the key of the extension entry
         * @param value the value of the extension entry
         *
         * @return the builder
         */
        B addExtension(String key, Object value);

        /**
         * @return a newly built {@link ExecutionResult}
         */
        ExecutionResult build();
    }
}
