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
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLUnionType;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.introspection.Introspection.INTROSPECTION_SYSTEM_FIELDS;
import static graphql.introspection.Introspection.isIntrospectionTypes;
import static java.lang.String.format;

/**
 * The validation about GraphQLObjectType, GraphQLInterfaceType, GraphQLUnionType, GraphQLEnumType and GraphQLInputObjectType:
 * <ul>
 *     <li>Types must define one or more fields;</li>
 *     <li>Enum type must define one or more enum values;</li>
 *     <li>Union type must include one or more unique member types;</li>
 *     <li>The member types of a Union type must all be Object base types;</li>
 *     <li>Names must not begin with "__" (two underscores).</li>
 * </ul>
 * <p>
 * details in https://spec.graphql.org/June2018/#sec-Type-System
 */
@Internal
@NullMarked
public class TypeAndFieldRule implements SchemaValidationRule {

    private final SchemaValidationErrorCollector errors;

    public TypeAndFieldRule(SchemaValidationErrorCollector errors) {
        this.errors = errors;
    }

    @Override
    public void checkObjectType(GraphQLObjectType objectType) {
        if (!isIntrospectionTypes(objectType)) {
            checkFieldsContainer(objectType, objectType.getFieldDefinitions());
        }
    }

    @Override
    public void checkInterfaceType(GraphQLInterfaceType interfaceType) {
        checkFieldsContainer(interfaceType, interfaceType.getFieldDefinitions());
    }

    @Override
    public void checkUnionType(GraphQLUnionType unionType) {
        if (!isIntrospectionTypes(unionType)) {
            checkUnion(unionType);
        }
    }

    @Override
    public void checkEnumType(GraphQLEnumType enumType) {
        if (!isIntrospectionTypes(enumType)) {
            checkEnum(enumType, enumType.getValues());
        }
    }

    @Override
    public void checkInputObjectType(GraphQLInputObjectType inputObjectType) {
        if (!isIntrospectionTypes(inputObjectType)) {
            checkInputObject(inputObjectType, inputObjectType.getFieldDefinitions());
        }
    }

    private void checkFieldsContainer(GraphQLFieldsContainer type, List<GraphQLFieldDefinition> fieldDefinitions) {
        if (fieldDefinitions.isEmpty()) {
            addError(SchemaValidationErrorType.ImplementingTypeLackOfFieldError, format("\"%s\" must define one or more fields.", type.getName()));
            return;
        }
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            checkFieldName(type.getName(), fieldDefinition.getName());
            for (GraphQLArgument argument : fieldDefinition.getArguments()) {
                checkArgumentName(type.getName(), fieldDefinition.getName(), argument.getName());
            }
        }
    }

    private void checkInputObject(GraphQLInputObjectType type, List<GraphQLInputObjectField> fields) {
        if (fields.isEmpty()) {
            addError(SchemaValidationErrorType.InputObjectTypeLackOfFieldError, format("\"%s\" must define one or more fields.", type.getName()));
            return;
        }
        for (GraphQLInputObjectField field : fields) {
            checkFieldName(type.getName(), field.getName());
        }
    }

    private void checkUnion(GraphQLUnionType type) {
        List<GraphQLNamedOutputType> memberTypes = type.getTypes();
        if (memberTypes.isEmpty()) {
            addError(SchemaValidationErrorType.UnionTypeLackOfTypeError, format("Union type \"%s\" must include one or more unique member types.", type.getName()));
        }
        Set<String> typeNames = new LinkedHashSet<>();
        for (GraphQLNamedOutputType memberType : memberTypes) {
            if (!(memberType instanceof GraphQLObjectType)) {
                addError(SchemaValidationErrorType.InvalidUnionMemberTypeError,
                        format("The member types of a Union type must all be Object base types. member type \"%s\" in Union \"%s\" is invalid.", memberType.getName(), type.getName()));
            }
            if (!typeNames.add(memberType.getName())) {
                addError(SchemaValidationErrorType.RepetitiveElementError,
                        format("The member types of a Union type must be unique. member type \"%s\" in Union \"%s\" is not unique.", memberType.getName(), type.getName()));
            }
        }
    }

    private void checkEnum(GraphQLEnumType type, List<GraphQLEnumValueDefinition> values) {
        if (values.isEmpty()) {
            addError(SchemaValidationErrorType.EnumLackOfValueError, format("Enum type \"%s\" must define one or more enum values.", type.getName()));
            return;
        }
        for (GraphQLEnumValueDefinition value : values) {
            if (isReservedName(value.getName())) {
                addError(SchemaValidationErrorType.InvalidCustomizedNameError,
                        format("EnumValueDefinition \"%s\" in  \"%s\"  must not begin with \"__\", which is reserved by GraphQL introspection.", value.getName(), type.getName()));
            }
        }
    }

    private void checkFieldName(String typeName, String fieldName) {
        if (isReservedName(fieldName) && !INTROSPECTION_SYSTEM_FIELDS.contains(fieldName)) {
            addError(SchemaValidationErrorType.InvalidCustomizedNameError,
                    format("\"%s\" in \"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", fieldName, typeName));
        }
    }

    private void checkArgumentName(String typeName, String fieldName, String argumentName) {
        if (isReservedName(argumentName)) {
            addError(SchemaValidationErrorType.InvalidCustomizedNameError,
                    format("Argument name \"%s\" in \"%s-%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", argumentName, typeName, fieldName));
        }
    }

    private boolean isReservedName(String name) {
        return name.startsWith("__");
    }

    private void addError(SchemaValidationErrorType errorType, String message) {
        errors.addError(new SchemaValidationError(errorType, message));
    }
}
