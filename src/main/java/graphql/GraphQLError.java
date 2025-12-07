package graphql;


import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * The interface describing graphql errors
 * <p>
 * NOTE: This class implements {@link java.io.Serializable} and hence it can be serialised and placed into a distributed cache.  However we
 * are not aiming to provide long term compatibility and do not intend for you to place this serialised data into permanent storage,
 * with times frames that cross graphql-java versions.  While we don't change things unnecessarily,  we may inadvertently break
 * the serialised compatibility across versions.
 *
 * @see <a href="https://spec.graphql.org/October2021/#sec-Errors">GraphQL Spec - 7.1.2 Errors</a>
 */
@PublicApi
@NullMarked
public interface GraphQLError extends Serializable {

    /**
     * @return a description of the error intended for the developer as a guide to understand and correct the error
     *
     * Non-nullable from the spec:
     * Every error must contain an entry with the key "message" with a string description of the error intended for
     * the developer as a guide to understand and correct the error.
     */
    String getMessage();

    /**
     * @return the location(s) within the GraphQL document at which the error occurred. Each {@link SourceLocation}
     * describes the beginning of an associated syntax element
     */
    @Nullable List<SourceLocation> getLocations();

    /**
     * @return an object classifying this error
     */
    ErrorClassification getErrorType();

    /**
     * The graphql spec says that the (optional) path field of any error must be
     * a list of path entries starting at the root of the response
     * and ending with the field associated with the error
     * <a href="https://spec.graphql.org/draft/#sec-Errors.Error-Result-Format">...</a>
     *
     * @return the path in list format
     */
    default @Nullable List<Object> getPath() {
        return null;
    }

    /**
     * The graphql specification says that result of a call should be a map that follows certain rules on what items
     * should be present.  Certain JSON serializers may or may interpret the error to spec, so this method
     * is provided to produce a map that strictly follows the specification.
     * <p>
     * See : <a href="https://spec.graphql.org/October2021/#sec-Errors">GraphQL Spec - 7.1.2 Errors</a>
     *
     * @return a map of the error that strictly follows the specification
     */
    default Map<String, Object> toSpecification() {
        return GraphqlErrorHelper.toSpecification(this);
    }

    /**
     * @return a map of error extensions or null if there are none
     */
    default @Nullable Map<String, Object> getExtensions() {
        return null;
    }

    /**
     * This can be called to turn a specification error map into {@link GraphQLError}
     *
     * @param specificationMap the map of values that should have come via {@link GraphQLError#toSpecification()}
     *
     * @return a {@link GraphQLError}
     */
    static GraphQLError fromSpecification(Map<String, Object> specificationMap) {
        return GraphqlErrorHelper.fromSpecification(specificationMap);
    }

    /**
     * @return a new builder of {@link GraphQLError}s
     */
    static Builder<?> newError() {
        return new GraphqlErrorBuilder<>();
    }

    /**
     * A builder of {@link GraphQLError}s
     */
    @NullUnmarked
    interface Builder<B extends Builder<B>> {

        /**
         * Sets the message of the error using {@link String#format(String, Object...)} with the arguments
         *
         * @param message    the message
         * @param formatArgs the arguments to use
         *
         * @return this builder
         */
        B message(String message, Object... formatArgs);

        /**
         * This adds locations to the error
         *
         * @param locations the locations to add
         *
         * @return this builder
         */
        B locations(List<SourceLocation> locations);

        /**
         * This adds a location to the error
         *
         * @param location the locations to add
         *
         * @return this builder
         */
        B location(SourceLocation location);

        /**
         * Sets the path of the message
         *
         * @param path can be null
         *
         * @return this builder
         */
        B path(ResultPath path);

        /**
         * Sets the path of the message
         *
         * @param path can be null
         *
         * @return this builder
         */
        B path(List<Object> path);

        /**
         * Sets the {@link ErrorClassification} of the message
         *
         * @param errorType the error classification to use
         *
         * @return this builder
         */
        B errorType(ErrorClassification errorType);

        /**
         * Sets the extensions of the message
         *
         * @param extensions the extensions to use
         *
         * @return this builder
         */
        B extensions(Map<String, Object> extensions);

        /**
         * @return a newly built GraphqlError
         */
        GraphQLError build();
    }
}
