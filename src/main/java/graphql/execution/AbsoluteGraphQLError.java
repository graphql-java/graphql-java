package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;
import graphql.schema.DataFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * A {@link GraphQLError} that has been changed from a {@link DataFetcher} relative error to an absolute one.
 */
class AbsoluteGraphQLError implements GraphQLError {

    private final List<SourceLocation> locations;
    private final List<Object> absolutePath;
    private final GraphQLError relativeError;
    private final String message;
    private final ErrorType errorType;

    AbsoluteGraphQLError(ExecutionStrategyParameters executionStrategyParameters, GraphQLError relativeError) {
        requireNonNull(executionStrategyParameters);
        this.relativeError = requireNonNull(relativeError);
        this.absolutePath = Optional.ofNullable(relativeError.getPath())
                .map(originalPath -> {
                    List<Object> path = new ArrayList<>();
                    path.addAll(executionStrategyParameters.path().toList());
                    path.addAll(relativeError.getPath());
                    return path;
                })
                .orElse(null);

        Optional<SourceLocation> baseLocation;
        if (!executionStrategyParameters.field().isEmpty()) {
            baseLocation = Optional.ofNullable(executionStrategyParameters.field().get(0).getSourceLocation());
        } else {
            baseLocation = Optional.empty();
        }

        this.locations = Optional.ofNullable(
                relativeError.getLocations())
                .map(locations -> locations.stream()
                        .map(l ->
                                baseLocation
                                        .map(base -> new SourceLocation(base.getLine() + l.getLine(), base.getColumn() + l.getColumn()))
                                        .orElse(null))
                        .collect(Collectors.toList()))
                .orElse(null);
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
}
