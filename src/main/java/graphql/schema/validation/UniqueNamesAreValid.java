package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.introspection.Introspection.isIntrospectionTypes;
import static graphql.schema.validation.SchemaValidationErrorType.DuplicateArgumentName;
import static graphql.schema.validation.SchemaValidationErrorType.DuplicateEnumValue;
import static graphql.schema.validation.SchemaValidationErrorType.DuplicateFieldName;
import static graphql.schema.validation.SchemaValidationErrorType.DuplicateInputFieldName;
import static java.lang.String.format;

/**
 * Validates that names are unique within their containing type:
 * <ul>
 *     <li>Field names within object types and interface types</li>
 *     <li>Input field names within input object types</li>
 *     <li>Argument names within field definitions</li>
 *     <li>Enum value names within enum types</li>
 * </ul>
 */
@Internal
public class UniqueNamesAreValid extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType type, TraverserContext<GraphQLSchemaElement> context) {
        if (isIntrospectionTypes(type)) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        checkFieldUniqueness(type, errorCollector);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType type, TraverserContext<GraphQLSchemaElement> context) {
        if (isIntrospectionTypes(type)) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        checkFieldUniqueness(type, errorCollector);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType type, TraverserContext<GraphQLSchemaElement> context) {
        if (isIntrospectionTypes(type)) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        checkInputFieldUniqueness(type, errorCollector);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        checkArgumentUniqueness(fieldDefinition, errorCollector);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLEnumType(GraphQLEnumType type, TraverserContext<GraphQLSchemaElement> context) {
        if (isIntrospectionTypes(type)) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        checkEnumValueUniqueness(type, errorCollector);
        return TraversalControl.CONTINUE;
    }

    private void checkFieldUniqueness(GraphQLFieldsContainer type, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLFieldDefinition> fields = type.getFieldDefinitions();
        Set<String> seen = new LinkedHashSet<>();
        for (GraphQLFieldDefinition field : fields) {
            if (!seen.add(field.getName())) {
                errorCollector.addError(new SchemaValidationError(DuplicateFieldName,
                        format("Type '%s' has a duplicate field named '%s'", type.getName(), field.getName())));
            }
        }
    }

    private void checkInputFieldUniqueness(GraphQLInputObjectType type, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLInputObjectField> fields = type.getFields();
        Set<String> seen = new LinkedHashSet<>();
        for (GraphQLInputObjectField field : fields) {
            if (!seen.add(field.getName())) {
                errorCollector.addError(new SchemaValidationError(DuplicateInputFieldName,
                        format("Input type '%s' has a duplicate field named '%s'", type.getName(), field.getName())));
            }
        }
    }

    private void checkArgumentUniqueness(GraphQLFieldDefinition fieldDefinition, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLArgument> arguments = fieldDefinition.getArguments();
        Set<String> seen = new LinkedHashSet<>();
        for (GraphQLArgument argument : arguments) {
            if (!seen.add(argument.getName())) {
                errorCollector.addError(new SchemaValidationError(DuplicateArgumentName,
                        format("Field '%s' has a duplicate argument named '%s'", fieldDefinition.getName(), argument.getName())));
            }
        }
    }

    private void checkEnumValueUniqueness(GraphQLEnumType type, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLEnumValueDefinition> values = type.getValues();
        Set<String> seen = new LinkedHashSet<>();
        for (GraphQLEnumValueDefinition value : values) {
            if (!seen.add(value.getName())) {
                errorCollector.addError(new SchemaValidationError(DuplicateEnumValue,
                        format("Enum type '%s' has a duplicate value named '%s'", type.getName(), value.getName())));
            }
        }
    }
}
