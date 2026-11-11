package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import org.jspecify.annotations.NullMarked;

import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static java.lang.String.format;

/**
 * Schema validation rule ensuring output types are only used in output positions and input types only in input positions.
 */
@Internal
@NullMarked
public class InputAndOutputTypesUsedAppropriately implements SchemaValidationRule {

    private final SchemaValidationErrorCollector errors;

    public InputAndOutputTypesUsedAppropriately(SchemaValidationErrorCollector errors) {
        this.errors = errors;
    }

    @Override
    public void checkFieldDefinition(GraphQLFieldsContainer container, GraphQLFieldDefinition fieldDefinition) {
        for (GraphQLArgument argument : fieldDefinition.getArguments()) {
            if (hasMisusedType(argument.getType(), true)) {
                String path = container.getName() + "." + fieldDefinition.getName() + "." + argument.getName();
                addTypeContextErrors(argument.getType(), true, path);
            }
        }
        if (hasMisusedType(fieldDefinition.getType(), false)) {
            String path = container.getName() + "." + fieldDefinition.getName();
            addTypeContextErrors(fieldDefinition.getType(), false, path);
        }
    }

    @Override
    public void checkInputObjectField(GraphQLInputObjectType type, GraphQLInputObjectField inputObjectField) {
        if (hasMisusedType(inputObjectField.getType(), true)) {
            String path = type.getName() + "." + inputObjectField.getName();
            addTypeContextErrors(inputObjectField.getType(), true, path);
        }
    }

    private boolean hasMisusedType(GraphQLType type, boolean inputContext) {
        GraphQLType current = type;
        while (true) {
            if (isMisused(current, inputContext)) {
                return true;
            }
            if (!(current instanceof GraphQLModifiedType)) {
                return false;
            }
            current = ((GraphQLModifiedType) current).getWrappedType();
        }
    }

    private void addTypeContextErrors(GraphQLType type, boolean inputContext, String path) {
        GraphQLType current = type;
        while (true) {
            if (isMisused(current, inputContext)) {
                addTypeContextError(current, inputContext, path);
            }
            if (!(current instanceof GraphQLModifiedType)) {
                return;
            }
            current = ((GraphQLModifiedType) current).getWrappedType();
        }
    }

    private void addTypeContextError(GraphQLType misusedType, boolean inputContext, String path) {
        if (inputContext) {
            addError(SchemaValidationErrorType.OutputTypeUsedInInputTypeContext,
                    format("The output type '%s' has been used in an input type context : '%s'", simplePrint(misusedType), path));
        } else {
            addError(SchemaValidationErrorType.InputTypeUsedInOutputTypeContext,
                    format("The input type '%s' has been used in a output type context : '%s'", simplePrint(misusedType), path));
        }
    }

    private boolean isMisused(GraphQLType type, boolean inputContext) {
        return inputContext ? !(type instanceof GraphQLInputType) : !(type instanceof GraphQLOutputType);
    }

    private void addError(SchemaValidationErrorType errorType, String message) {
        errors.addError(new SchemaValidationError(errorType, message));
    }
}
