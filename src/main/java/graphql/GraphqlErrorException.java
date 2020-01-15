package graphql;

import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A base class for graphql runtime exceptions that also implement {@link graphql.GraphQLError} and can be used
 * in a general sense direct or have specialisations made of it.
 * <p>
 * This is aimed amongst other reasons at Kotlin consumers due to https://github.com/graphql-java/graphql-java/issues/1690
 * as well as being a way to share common code.
 */
@PublicApi
public class GraphqlErrorException extends GraphQLException implements GraphQLError {

    protected final List<SourceLocation> locations;
    protected final Map<String, Object> extensions;
    protected final List<Object> path;
    protected final ErrorClassification errorClassification;

    protected GraphqlErrorException(BuilderBase<?> builder) {
        super(builder.message, builder.cause);
        this.locations = builder.sourceLocations;
        this.extensions = builder.extensions;
        this.path = builder.path;
        this.errorClassification = builder.errorClassification;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    public ErrorClassification getErrorType() {
        return errorClassification;
    }

    @Override
    public List<Object> getPath() {
        return path;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public static Builder newErrorException() {
        return new Builder();
    }

    public static class Builder extends BuilderBase<Builder> {
        public GraphqlErrorException build() {
            return new GraphqlErrorException(this);
        }
    }

    protected static class BuilderBase<T extends BuilderBase<T>> {
        protected String message;
        protected Throwable cause;
        protected ErrorClassification errorClassification = ErrorType.DataFetchingException;
        protected List<SourceLocation> sourceLocations;
        protected Map<String, Object> extensions;
        protected List<Object> path;

        private T asDerivedType() {
            //noinspection unchecked
            return (T) this;
        }

        public T message(String message) {
            this.message = message;
            return asDerivedType();
        }

        public T cause(Throwable cause) {
            this.cause = cause;
            return asDerivedType();
        }

        public T sourceLocation(SourceLocation sourceLocation) {
            return sourceLocations(sourceLocation == null ? null : Collections.singletonList(sourceLocation));
        }

        public T sourceLocations(List<SourceLocation> sourceLocations) {
            this.sourceLocations = sourceLocations;
            return asDerivedType();
        }

        public T errorClassification(ErrorClassification errorClassification) {
            this.errorClassification = errorClassification;
            return asDerivedType();
        }

        public T path(List<Object> path) {
            this.path = path;
            return asDerivedType();
        }

        public T extensions(Map<String, Object> extensions) {
            this.extensions = extensions;
            return asDerivedType();
        }

    }
}
