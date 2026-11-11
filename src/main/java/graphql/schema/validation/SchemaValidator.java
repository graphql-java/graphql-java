package graphql.schema.validation;

import graphql.Internal;
import graphql.execution.InputValueLimits;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLUnionType;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Set;

/**
 * Validates a schema by visiting each of its types, their fields, arguments, input fields and enum values, and each
 * directive definition once, calling the matching {@link SchemaValidationRule} hooks for every element.
 */
@Internal
@NullMarked
public class SchemaValidator {

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        SchemaValidationErrorCollector errors = new SchemaValidationErrorCollector();
        new DefaultValueDepth(InputValueLimits.getDefaultLimits().getMaxDepth()).check(schema, errors);
        if (!errors.getErrors().isEmpty()) {
            return errors.getErrors();
        }
        List<GraphQLNamedType> types = schema.getAllTypesAsList();

        // runs first because value validation must not coerce default values that form a cycle
        checkDefaultValueCycles(types, errors);

        SchemaValidationRule[] rules = createRules(schema, errors);
        for (GraphQLNamedType type : types) {
            validateType(type, rules);
        }
        for (GraphQLDirective directive : schema.getDirectives()) {
            validateDirectiveDefinition(directive, rules);
        }
        return errors.getErrors();
    }

    private void checkDefaultValueCycles(List<GraphQLNamedType> types, SchemaValidationErrorCollector errors) {
        NoDefaultValueCircularRefs noDefaultValueCircularRefs = new NoDefaultValueCircularRefs(errors);
        for (GraphQLNamedType type : types) {
            if (type instanceof GraphQLInputObjectType) {
                noDefaultValueCircularRefs.checkInputObjectType((GraphQLInputObjectType) type);
            }
        }
    }

    private SchemaValidationRule[] createRules(GraphQLSchema schema, SchemaValidationErrorCollector errors) {
        InputValueValidator inputValueValidator = new InputValueValidator(schema, errors);
        return new SchemaValidationRule[]{
                new NoUnbrokenInputCycles(errors),
                new TypesImplementInterfaces(errors),
                new TypeAndFieldRule(errors),
                new DefaultValuesAreValid(inputValueValidator, errors),
                new AppliedDirectivesAreValid(schema, errors),
                new AppliedDirectiveArgumentsAreValid(inputValueValidator, errors),
                new InputAndOutputTypesUsedAppropriately(errors),
                new OneOfInputObjectRules(errors),
                new DeprecatedInputObjectAndArgumentsAreValid(errors),
        };
    }

    private void validateType(GraphQLNamedType type, SchemaValidationRule[] rules) {
        if (type instanceof GraphQLObjectType) {
            validateObjectType((GraphQLObjectType) type, rules);
        } else if (type instanceof GraphQLInterfaceType) {
            validateInterfaceType((GraphQLInterfaceType) type, rules);
        } else if (type instanceof GraphQLInputObjectType) {
            validateInputObjectType((GraphQLInputObjectType) type, rules);
        } else if (type instanceof GraphQLEnumType) {
            validateEnumType((GraphQLEnumType) type, rules);
        } else if (type instanceof GraphQLUnionType) {
            validateUnionType((GraphQLUnionType) type, rules);
        } else if (type instanceof GraphQLScalarType) {
            checkDirectiveContainer((GraphQLScalarType) type, rules);
        }
    }

    private void validateObjectType(GraphQLObjectType type, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkObjectType(type);
        }
        checkDirectiveContainer(type, rules);
        validateFieldDefinitions(type, rules);
    }

    private void validateInterfaceType(GraphQLInterfaceType type, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkInterfaceType(type);
        }
        checkDirectiveContainer(type, rules);
        validateFieldDefinitions(type, rules);
    }

    private void validateUnionType(GraphQLUnionType type, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkUnionType(type);
        }
        checkDirectiveContainer(type, rules);
    }

    private void validateEnumType(GraphQLEnumType type, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkEnumType(type);
        }
        checkDirectiveContainer(type, rules);
        for (GraphQLEnumValueDefinition value : type.getValues()) {
            checkDirectiveContainer(value, rules);
        }
    }

    private void validateInputObjectType(GraphQLInputObjectType type, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkInputObjectType(type);
        }
        checkDirectiveContainer(type, rules);
        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            validateInputObjectField(type, field, rules);
        }
    }

    private void validateFieldDefinitions(GraphQLFieldsContainer container, SchemaValidationRule[] rules) {
        for (GraphQLFieldDefinition fieldDefinition : container.getFieldDefinitions()) {
            validateFieldDefinition(container, fieldDefinition, rules);
        }
    }

    private void validateFieldDefinition(GraphQLFieldsContainer container, GraphQLFieldDefinition fieldDefinition, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkFieldDefinition(container, fieldDefinition);
        }
        checkDirectiveContainer(fieldDefinition, rules);
        for (GraphQLArgument argument : fieldDefinition.getArguments()) {
            validateArgument(fieldDefinition, argument, rules);
        }
    }

    private void validateInputObjectField(GraphQLInputObjectType type, GraphQLInputObjectField field, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkInputObjectField(type, field);
        }
        checkDirectiveContainer(field, rules);
    }

    private void validateArgument(GraphQLSchemaElement fieldOrDirective, GraphQLArgument argument, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkArgument(fieldOrDirective, argument);
        }
        checkDirectiveContainer(argument, rules);
    }

    private void validateDirectiveDefinition(GraphQLDirective directive, SchemaValidationRule[] rules) {
        checkDirectiveContainer(directive, rules);
        for (GraphQLArgument argument : directive.getArguments()) {
            validateArgument(directive, argument, rules);
        }
    }

    private void checkDirectiveContainer(GraphQLDirectiveContainer container, SchemaValidationRule[] rules) {
        for (SchemaValidationRule rule : rules) {
            rule.checkDirectiveContainer(container);
        }
    }
}
