package graphql.schema.validation.rule;


import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.exception.SchemaValidationErrorType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The validation about GraphQLObjectType, GraphQLInterfaceType, GraphQLUnionType, GraphQLEnumType, GraphQLInputObjectType, GraphQLScalarType.
 * <p>
 * details in https://spec.graphql.org/June2018/#sec-Type-System
 */
public class TypeRule implements SchemaValidationRule {

    private Map<String, GraphQLNamedType> schemaTypeHolder;

    @Override
    public void apply(GraphQLSchema graphQLSchema, SchemaValidationErrorCollector validationErrorCollector) {

        List<GraphQLNamedType> allTypesAsList = graphQLSchema.getAllTypesAsList();

        List<GraphQLNamedType> filteredType = filterScalarAndIntrospectionTypes(allTypesAsList);

        schemaTypeHolder = graphQLSchema.getTypeMap();

        checkTypes(filteredType, validationErrorCollector);
    }


    private void checkTypes(List<GraphQLNamedType> customizedType, SchemaValidationErrorCollector errorCollector) {
        if (customizedType == null || customizedType.isEmpty()) {
            return;
        }

        for (GraphQLType type : customizedType) {
            checkType(type, errorCollector);
        }
    }

    private void checkType(GraphQLType type, SchemaValidationErrorCollector errorCollector) {
        if (type instanceof GraphQLObjectType) {
            validateObject((GraphQLObjectType) type, errorCollector);
        } else if (type instanceof GraphQLInterfaceType) {
            validateInterface((GraphQLInterfaceType) type, errorCollector);
        } else if (type instanceof GraphQLUnionType) {
            validateUnion((GraphQLUnionType) type, errorCollector);
        } else if (type instanceof GraphQLEnumType) {
            validateEnum((GraphQLEnumType) type, errorCollector);
        } else if (type instanceof GraphQLInputObjectType) {
            validateInputObject((GraphQLInputObjectType) type, errorCollector);
        } else if (type instanceof GraphQLScalarType) {
            validateScalar((GraphQLScalarType) type, errorCollector);
        }
    }

    private void validateObject(GraphQLObjectType type, SchemaValidationErrorCollector errorCollector) {
        assertTypeName(type.getName(), errorCollector);

        List<GraphQLFieldDefinition> fieldDefinitions = type.getFieldDefinitions();
        if (fieldDefinitions == null || fieldDefinitions.size() == 0) {
            SchemaValidationError validationError = new SchemaValidationError(SchemaValidationErrorType.GraphQLTypeError, type.getName() + "  must define one or more fields.");
            errorCollector.addError(validationError);
            return;
        }

        if (fieldDefinitions != null && fieldDefinitions.size() != 0) {
            for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
                validateFieldDefinition(type.getName(), fieldDefinition, errorCollector);
            }
        }
    }

    private void validateInterface(GraphQLInterfaceType type, SchemaValidationErrorCollector errorCollector) {
        assertTypeName(type.getName(), errorCollector);
        List<GraphQLFieldDefinition> fieldDefinitions = type.getFieldDefinitions();
        if (fieldDefinitions == null || fieldDefinitions.size() == 0) {
            SchemaValidationError validationError = new SchemaValidationError(SchemaValidationErrorType.GraphQLTypeError, type.getName() + "  must define one or more fields.");
            errorCollector.addError(validationError);
        }

        if (fieldDefinitions != null && fieldDefinitions.size() != 0) {
            for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
                validateFieldDefinition(type.getName(),fieldDefinition, errorCollector);
            }
        }
    }

    private void validateInputObject(GraphQLInputObjectType type, SchemaValidationErrorCollector errorCollector) {
        assertTypeName(type.getName(), errorCollector);
        if (type.getFieldDefinitions() == null || type.getFieldDefinitions().size() == 0) {
            SchemaValidationError validationError = new SchemaValidationError(SchemaValidationErrorType.GraphQLTypeError, type.getName() + "  must define one or more fields.");
            errorCollector.addError(validationError);
        }

    }

    private void validateUnion(GraphQLUnionType type, SchemaValidationErrorCollector errorCollector) {
        assertTypeName(type.getName(), errorCollector);

        UnionTypeDefinition definition = type.getDefinition();
        if (definition == null) {
            return;
        }

        List<Type> memberTypes = definition.getMemberTypes();
        if (memberTypes == null || memberTypes.size() == 0) {
            SchemaValidationError validationError =
                    new SchemaValidationError(SchemaValidationErrorType.GraphQLUnionTypeError, String.format("Union type \"%s\" must include one or more unique member types.", type.getName()));
            errorCollector.addError(validationError);
        }

        for (Type memberType : memberTypes) {
            String typeName = ((TypeName) memberType).getName();
            GraphQLNamedType graphQLNamedType = schemaTypeHolder.get(typeName);
            if (!(graphQLNamedType instanceof GraphQLObjectType)) {
                SchemaValidationError validationError =
                        new SchemaValidationError(SchemaValidationErrorType.GraphQLUnionTypeError, String.format("The member types of a Union type must all be Object base types. member type \"%s\" in Union \"%s\" is invalid", type.getName(), memberType.getClass().getSimpleName()));
                errorCollector.addError(validationError);
            }
        }
    }

    private void validateScalar(GraphQLScalarType type, SchemaValidationErrorCollector errorCollector) {
        assertTypeName(type.getName(), errorCollector);
    }

    private void validateEnum(GraphQLEnumType type, SchemaValidationErrorCollector errorCollector) {
        assertTypeName(type.getName(), errorCollector);

        List<GraphQLEnumValueDefinition> enumValueDefinitions = type.getValues();
        if(enumValueDefinitions==null||enumValueDefinitions.size()==0){
            SchemaValidationError validationError = new SchemaValidationError(SchemaValidationErrorType.GraphQLEnumError, String.format("Enum type \"%s\"  must define one or more unique enum values.",type.getName()));
            errorCollector.addError(validationError);
        }else{
            for (GraphQLEnumValueDefinition enumValueDefinition : enumValueDefinitions) {
                assertEnumValueDefinitionName(type.getName(),enumValueDefinition.getName(),errorCollector);
            }
        }

    }

    private void validateFieldDefinition(String typeName, GraphQLFieldDefinition fieldDefinition, SchemaValidationErrorCollector errorCollector) {
        assertFieldName(typeName, fieldDefinition.getName(), errorCollector);
        assertNonNullType(fieldDefinition.getType(),errorCollector);

        List<GraphQLArgument> fieldDefinitionArguments = fieldDefinition.getArguments();
        if(fieldDefinitionArguments!=null||fieldDefinitionArguments.size()!=0){
            for (GraphQLArgument fieldDefinitionArgument : fieldDefinitionArguments) {
                validateFieldDefinitionArgument(typeName,fieldDefinition.getName(),fieldDefinitionArgument,errorCollector);
            }
        }
    }

    private void validateFieldDefinitionArgument(String typeName, String fieldName,GraphQLArgument argument, SchemaValidationErrorCollector errorCollector) {
        assertArgumentName(typeName,fieldName,argument.getName(),errorCollector);
        assertNonNullType(argument.getType(),errorCollector);
    }

    private void assertTypeName(String typeName, SchemaValidationErrorCollector validationErrorCollector) {
        if (typeName.length() >= 2 && typeName.startsWith("__")) {
            SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.InValidName,
                    String.format("\"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", typeName));
            validationErrorCollector.addError(schemaValidationError);
        }
    }

    private void assertFieldName(String typeName, String fieldName, SchemaValidationErrorCollector errorCollector) {
        if (fieldName.length() >= 2 && fieldName.startsWith("__")) {
            SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.InValidName,
                    String.format("\"%s\" in \"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", fieldName, typeName));
            errorCollector.addError(schemaValidationError);
        }
    }

    private void assertArgumentName(String typeName, String fieldName, String argumentName, SchemaValidationErrorCollector errorCollector) {
        if (argumentName.length() >= 2 && argumentName.startsWith("__")) {
            SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.InValidName,
                    String.format("Argument name \"%s\" in \"%s-%s\" must not begin with \"__\", which is reserved by GraphQL introspection.",argumentName, typeName,fieldName));
            errorCollector.addError(schemaValidationError);
        }
    }

    private void assertEnumValueDefinitionName(String typeName, String enumValueDefinitionName, SchemaValidationErrorCollector errorCollector) {
        if (enumValueDefinitionName.length() >= 2 && enumValueDefinitionName.startsWith("__")) {
            SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.DirectiveInvalidError,
                    String.format("EnumValueDefinition \"%s\" in  \"%s\"  must not begin with \"__\", which is reserved by GraphQL introspection.", enumValueDefinitionName, typeName));
            errorCollector.addError(schemaValidationError);
        }
    }

    private void assertNonNullType(GraphQLType type, SchemaValidationErrorCollector errorCollector){
        if(type instanceof GraphQLNonNull&& ((GraphQLNonNull) type).getWrappedType() instanceof GraphQLNonNull){
            SchemaValidationError schemaValidationError=new SchemaValidationError(SchemaValidationErrorType.GraphQLTypeError,
                    String.format("Non‐Null type \"%s\" must not wrap another Non‐Null type.",GraphQLTypeUtil.simplePrint(type)));
            errorCollector.addError(schemaValidationError);
        }
    }

    private List<GraphQLNamedType> filterScalarAndIntrospectionTypes(List<GraphQLNamedType> graphQLNamedTypes) {
        if (graphQLNamedTypes == null || graphQLNamedTypes.isEmpty()) {
            return Collections.emptyList();
        }

        return graphQLNamedTypes.stream().filter(type -> !isScalarOrIntrospectionTypes(type)).collect(Collectors.toList());
    }

    private boolean isScalarOrIntrospectionTypes(GraphQLNamedType type) {
        if (type instanceof GraphQLScalarType && (
                type.getName().equals("Int") || type.getName().equals("Float") || type.getName().equals("String") ||
                        type.getName().equals("Boolean") || type.getName().equals("ID") || type.getName().equals("Long") || type.getName().equals("Short") ||
                        type.getName().equals("BigInteger") || type.getName().equals("BigDecimal") || type.getName().equals("Char")
        )) {
            return true;
        }

        if (type instanceof GraphQLEnumType && (type.getName().equals("__DirectiveLocation") || type.getName().equals("__TypeKind"))) {
            return true;
        }

        if (type instanceof GraphQLObjectType && (
                type.getName().equals("IntrospectionQuery") || type.getName().equals("__Type") || type.getName().equals("__Schema")
                        || type.getName().equals("__InputValue") || type.getName().equals("__Field") || type.getName().equals("__EnumValue")
                        || type.getName().equals("__Directive")
        )) {
            return true;
        }

        return false;
    }

}
