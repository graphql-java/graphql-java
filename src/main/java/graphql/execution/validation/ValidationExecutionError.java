package graphql.execution.validation;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.PublicApi;
import graphql.execution.ExecutionPath;
import graphql.language.Field;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;

/**
 * An error that represents validation execution errors
 */
@PublicApi
public class ValidationExecutionError implements GraphQLError {

    private final String message;
    private List<SourceLocation> sourceLocations;
    private final List<Object> path;

    public ValidationExecutionError(String message, ExecutionPath executionPath, Field field) {
        this.message = message;
        this.path = executionPath.toList();
        this.sourceLocations = Collections.singletonList(field.getSourceLocation());
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    @Override
    public List<Object> getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        return GraphqlErrorHelper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return GraphqlErrorHelper.hashCode(this);
    }

}
