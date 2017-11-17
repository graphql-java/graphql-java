package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.Field;
import graphql.language.SourceLocation;
import graphql.schema.DataFetcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;

/**
 * A {@link GraphQLError} that has been changed from a {@link DataFetcher} relative error to an absolute one.
 */
class AbsoluteGraphQLError implements GraphQLError {

    private final List<SourceLocation> locations;
    private final List<Object> absolutePath;
    private final String message;
    private final ErrorType errorType;

    AbsoluteGraphQLError(ExecutionStrategyParameters executionStrategyParameters, GraphQLError relativeError) {
        assertNotNull(executionStrategyParameters);
        assertNotNull(relativeError);
        this.absolutePath = createAbsolutePath(executionStrategyParameters, relativeError);
        this.locations = createAbsoluteLocations(relativeError, executionStrategyParameters.field());
        this.message = relativeError.getMessage();
        this.errorType = relativeError.getErrorType();
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    public ErrorType getErrorType() {
        return errorType;
    }

    @Override
    public List<Object> getPath() {
        return absolutePath;
    }

    private List<Object> createAbsolutePath(ExecutionStrategyParameters executionStrategyParameters,
                                            GraphQLError relativeError) {
        return Optional.ofNullable(relativeError.getPath())
                .map(originalPath -> {
                    List<Object> path = new ArrayList<>();
                    path.addAll(executionStrategyParameters.path().toList());
                    path.addAll(relativeError.getPath());
                    return path;
                })
                .map(Collections::unmodifiableList)
                .orElse(null);
    }

    private List<SourceLocation> createAbsoluteLocations(GraphQLError relativeError, List<Field> fields) {
        Optional<SourceLocation> baseLocation;
        if (!fields.isEmpty()) {
            baseLocation = Optional.ofNullable(fields.get(0).getSourceLocation());
        } else {
            baseLocation = Optional.empty();
        }
        return Optional.ofNullable(
                relativeError.getLocations())
                .map(locations -> locations.stream()
                        .map(l ->
                                baseLocation
                                        .map(base -> new SourceLocation(
                                                base.getLine() + l.getLine(),
                                                base.getColumn() + l.getColumn()))
                                        .orElse(null))
                        .collect(Collectors.toList()))
                .map(Collections::unmodifiableList)
                .orElse(null);
    }
}
