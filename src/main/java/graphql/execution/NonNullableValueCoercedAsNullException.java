package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

/**
 * This is thrown if a non nullable value is coerced to a null value
 */
@PublicApi
@NullMarked
public class NonNullableValueCoercedAsNullException extends GraphQLException implements GraphQLError {
    private @Nullable List<SourceLocation> sourceLocations;
    private @Nullable List<Object> path;

    public NonNullableValueCoercedAsNullException(VariableDefinition variableDefinition, GraphQLType graphQLType) {
        super(format("Variable '%s' has coerced Null value for NonNull type '%s'",
                variableDefinition.getName(), GraphQLTypeUtil.simplePrint(graphQLType)));
        this.sourceLocations = Collections.singletonList(variableDefinition.getSourceLocation());
    }

    public NonNullableValueCoercedAsNullException(VariableDefinition variableDefinition, String fieldName, GraphQLType graphQLType) {
        super(format("Field '%s' of variable '%s' has coerced Null value for NonNull type '%s'",
                fieldName, variableDefinition.getName(), GraphQLTypeUtil.simplePrint(graphQLType)));
        this.sourceLocations = Collections.singletonList(variableDefinition.getSourceLocation());
    }

    public NonNullableValueCoercedAsNullException(VariableDefinition variableDefinition, String fieldName, List<Object> path, GraphQLType graphQLType) {
        super(format("Field '%s' of variable '%s' has coerced Null value for NonNull type '%s'",
                fieldName, variableDefinition.getName(), GraphQLTypeUtil.simplePrint(graphQLType)));
        this.sourceLocations = Collections.singletonList(variableDefinition.getSourceLocation());
        this.path = path;
    }

    public NonNullableValueCoercedAsNullException(GraphQLType graphQLType) {
        super(format("Coerced Null value for NonNull type '%s'", GraphQLTypeUtil.simplePrint(graphQLType)));
    }

    public NonNullableValueCoercedAsNullException(VariableDefinition variableDefinition, String causeMessage) {
        super(format("Variable '%s' has an invalid value: %s", variableDefinition.getName(), causeMessage));
        this.sourceLocations = Collections.singletonList(variableDefinition.getSourceLocation());
    }

    public NonNullableValueCoercedAsNullException(String fieldName, List<Object> path, GraphQLType graphQLType) {
        super(format("Field '%s' has coerced Null value for NonNull type '%s'",
                fieldName, GraphQLTypeUtil.simplePrint(graphQLType)));
        this.path = path;
    }

    public NonNullableValueCoercedAsNullException(GraphQLInputObjectField inputTypeField) {
        super(format("Input field '%s' has coerced Null value for NonNull type '%s'",
                inputTypeField.getName(), GraphQLTypeUtil.simplePrint(inputTypeField.getType())));
    }

    public NonNullableValueCoercedAsNullException(GraphQLInputObjectField inputTypeField, List<Object> path) {
        super(format("Input field '%s' has coerced Null value for NonNull type '%s'",
                inputTypeField.getName(), GraphQLTypeUtil.simplePrint(inputTypeField.getType())));
        this.path = path;
    }

    public NonNullableValueCoercedAsNullException(GraphQLArgument graphQLArgument) {
        super(format("Argument '%s' has coerced Null value for NonNull type '%s'", graphQLArgument.getName(), graphQLArgument.getType()));
    }

    @Override
    public @Nullable List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public @Nullable List<Object> getPath() {
        return path;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }
}
