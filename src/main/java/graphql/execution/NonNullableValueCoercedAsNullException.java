package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import java.util.Collections;
import java.util.List;

import static java.lang.String.format;

/**
 * This is thrown if a non nullable value is coerced to a null value
 */
@PublicApi
public class NonNullableValueCoercedAsNullException extends GraphQLException implements GraphQLError {
    private List<SourceLocation> sourceLocations;
    private List<Object> path;

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

    public NonNullableValueCoercedAsNullException(GraphQLInputObjectField inputTypeField) {
        super(format("Input field '%s' has coerced Null value for NonNull type '%s'",
                inputTypeField.getName(), GraphQLTypeUtil.simplePrint(inputTypeField.getType())));
    }

    public NonNullableValueCoercedAsNullException(GraphQLInputObjectField inputTypeField, List<Object> path) {
        super(format("Input field '%s' has coerced Null value for NonNull type '%s'",
                inputTypeField.getName(), GraphQLTypeUtil.simplePrint(inputTypeField.getType())));
        this.path = path;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public List<Object> getPath() {
        return path;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }
}
