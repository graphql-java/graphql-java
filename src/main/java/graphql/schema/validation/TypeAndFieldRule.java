package graphql.schema.validation;

import graphql.collect.ImmutableKit;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static graphql.introspection.Introspection.INTROSPECTION_SYSTEM_FIELDS;
import static graphql.introspection.Introspection.isIntrospectionTypes;
import static graphql.schema.idl.ScalarInfo.isGraphqlSpecifiedScalar;

/**
 * The validation about GraphQLObjectType, GraphQLInterfaceType, GraphQLUnionType, GraphQLEnumType, GraphQLInputObjectType, GraphQLScalarType.
 *     <ul>
 *         <li>Types must define one or more fields;</li>
 *         <li>Enum type must define one or more enum values;</li>
 *         <li>Union type must include one or more unique member types;</li>
 *         <li>The member types of a Union type must all be Object base types;</li>
 *         <li>Non‐Null type must not wrap another Non‐Null type;</li>
 *         <li>Invalid name begin with "__" (two underscores).</li>
 *     </ul>
 * <p>
 * details in https://spec.graphql.org/June2018/#sec-Type-System
 */
public class TypeAndFieldRule extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType type, TraverserContext<GraphQLSchemaElement> context) {
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        validateContainsField((GraphQLFieldsContainer) type, errorCollector);
        return super.visitGraphQLInterfaceType(type, context);
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType type, TraverserContext<GraphQLSchemaElement> context) {
        if (isIntrospectionTypes(type)) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        validateContainsField((GraphQLFieldsContainer) type, errorCollector);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType type, TraverserContext<GraphQLSchemaElement> context) {
        if (isIntrospectionTypes(type)) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        validateUnion((GraphQLUnionType) type, errorCollector);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLEnumType(GraphQLEnumType type, TraverserContext<GraphQLSchemaElement> context) {
        if (isIntrospectionTypes(type)) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        validateEnum((GraphQLEnumType) type, errorCollector);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType type, TraverserContext<GraphQLSchemaElement> context) {
        if (isIntrospectionTypes(type)) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        validateInputObject((GraphQLInputObjectType) type, errorCollector);
        
        // OneOf validation: check if OneOf input types are inhabited
        if (type.isOneOf()) {
            if (!canBeProvidedAFiniteValue(type, new LinkedHashSet<>())) {
                String message = String.format("OneOf Input Object %s must be inhabited but all fields recursively reference only other OneOf Input Objects forming an unresolvable cycle.", type.getName());
                errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.OneOfNotInhabited, message));
            }
        }
        
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField inputObjectField, TraverserContext<GraphQLSchemaElement> context) {
        GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) context.getParentNode();
        if (!inputObjectType.isOneOf()) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        // OneOf validation: error messages taken from the reference implementation
        if (inputObjectField.hasSetDefaultValue()) {
            String message = String.format("OneOf input field %s.%s cannot have a default value.", inputObjectType.getName(), inputObjectField.getName());
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.OneOfDefaultValueOnField, message));
        }

        if (GraphQLTypeUtil.isNonNull(inputObjectField.getType())) {
            String message = String.format("OneOf input field %s.%s must be nullable.", inputObjectType.getName(), inputObjectField.getName());
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.OneOfNonNullableField, message));
        }
        return TraversalControl.CONTINUE;
    }


    private void validateContainsField(GraphQLFieldsContainer type, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLFieldDefinition> fieldDefinitions = type.getFieldDefinitions();
        if (fieldDefinitions == null || fieldDefinitions.size() == 0) {
            SchemaValidationError validationError = new SchemaValidationError(SchemaValidationErrorType.ImplementingTypeLackOfFieldError, String.format("\"%s\" must define one or more fields.", type.getName()));
            errorCollector.addError(validationError);
            return;
        }

        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            validateFieldDefinition(type.getName(), fieldDefinition, errorCollector);
        }
    }

    private void validateInputObject(GraphQLInputObjectType type, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLInputObjectField> inputObjectFields = type.getFields();
        if (inputObjectFields == null || inputObjectFields.size() == 0) {
            SchemaValidationError validationError = new SchemaValidationError(SchemaValidationErrorType.InputObjectTypeLackOfFieldError, String.format("\"%s\" must define one or more fields.", type.getName()));
            errorCollector.addError(validationError);
            return;
        }

        for (GraphQLInputObjectField inputObjectField : inputObjectFields) {
            validateInputFieldDefinition(type.getName(), inputObjectField, errorCollector);
        }
    }

    private boolean canBeProvidedAFiniteValue(GraphQLInputObjectType oneOfInputObject, Set<GraphQLInputObjectType> visited) {
        if (visited.contains(oneOfInputObject)) {
            return false;
        }
        Set<GraphQLInputObjectType> nextVisited = new LinkedHashSet<>(visited);
        nextVisited.add(oneOfInputObject);
        for (GraphQLInputObjectField field : oneOfInputObject.getFieldDefinitions()) {
            GraphQLType fieldType = field.getType();
            if (GraphQLTypeUtil.isList(fieldType)) {
                return true;
            }
            GraphQLUnmodifiedType namedFieldType = GraphQLTypeUtil.unwrapAll(fieldType);
            if (!(namedFieldType instanceof GraphQLInputObjectType)) {
                return true;
            }
            GraphQLInputObjectType inputFieldType = (GraphQLInputObjectType) namedFieldType;
            if (!inputFieldType.isOneOf()) {
                return true;
            }
            if (canBeProvidedAFiniteValue(inputFieldType, nextVisited)) {
                return true;
            }
        }
        return false;
    }

    private void validateUnion(GraphQLUnionType type, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLNamedOutputType> memberTypes = type.getTypes();
        if (memberTypes.size() == 0) {
            SchemaValidationError validationError =
                    new SchemaValidationError(SchemaValidationErrorType.UnionTypeLackOfTypeError, String.format("Union type \"%s\" must include one or more unique member types.", type.getName()));
            errorCollector.addError(validationError);
        }

        Set<String> typeNames = new HashSet<>();
        for (GraphQLNamedOutputType memberType : type.getTypes()) {
            String typeName = memberType.getName();
//            GraphQLNamedType graphQLNamedType = schemaTypeHolder.get(typeName);
            if (!(memberType instanceof GraphQLObjectType)) {
                SchemaValidationError validationError =
                        new SchemaValidationError(SchemaValidationErrorType.InvalidUnionMemberTypeError, String.format("The member types of a Union type must all be Object base types. member type \"%s\" in Union \"%s\" is invalid.", memberType.getName(), type.getName()));
                errorCollector.addError(validationError);
            }
            if (typeNames.contains(typeName)) {
                SchemaValidationError validationError =
                        new SchemaValidationError(SchemaValidationErrorType.RepetitiveElementError, String.format("The member types of a Union type must be unique. member type \"%s\" in Union \"%s\" is not unique.", memberType.getName(), type.getName()));
                errorCollector.addError(validationError);
            }
            typeNames.add(typeName);
        }
    }

    private void validateEnum(GraphQLEnumType type, SchemaValidationErrorCollector errorCollector) {
        List<GraphQLEnumValueDefinition> enumValueDefinitions = type.getValues();
        if (enumValueDefinitions == null || enumValueDefinitions.size() == 0) {
            SchemaValidationError validationError = new SchemaValidationError(SchemaValidationErrorType.EnumLackOfValueError, String.format("Enum type \"%s\" must define one or more enum values.", type.getName()));
            errorCollector.addError(validationError);
        } else {
            for (GraphQLEnumValueDefinition enumValueDefinition : enumValueDefinitions) {
                assertEnumValueDefinitionName(type.getName(), enumValueDefinition.getName(), errorCollector);
            }
        }

    }

    private void validateFieldDefinition(String typeName, GraphQLFieldDefinition fieldDefinition, SchemaValidationErrorCollector errorCollector) {
        assertFieldName(typeName, fieldDefinition.getName(), errorCollector);
        assertNonNullType(fieldDefinition.getType(), errorCollector);

        List<GraphQLArgument> fieldDefinitionArguments = fieldDefinition.getArguments();
        for (GraphQLArgument fieldDefinitionArgument : fieldDefinitionArguments) {
            validateFieldDefinitionArgument(typeName, fieldDefinition.getName(), fieldDefinitionArgument, errorCollector);
        }
    }

    private void validateInputFieldDefinition(String typeName, GraphQLInputObjectField inputObjectField, SchemaValidationErrorCollector errorCollector) {
        assertFieldName(typeName, inputObjectField.getName(), errorCollector);
        assertNonNullType(inputObjectField.getType(), errorCollector);
    }

    private void validateFieldDefinitionArgument(String typeName, String fieldName, GraphQLArgument argument, SchemaValidationErrorCollector errorCollector) {
        assertArgumentName(typeName, fieldName, argument.getName(), errorCollector);
        assertNonNullType(argument.getType(), errorCollector);
    }

    private void assertArgumentName(String typeName, String fieldName, String argumentName, SchemaValidationErrorCollector errorCollector) {
        if (argumentName.length() >= 2 && argumentName.startsWith("__")) {
            SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.InvalidCustomizedNameError,
                    String.format("Argument name \"%s\" in \"%s-%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", argumentName, typeName, fieldName));
            errorCollector.addError(schemaValidationError);
        }
    }

    private void assertEnumValueDefinitionName(String typeName, String enumValueDefinitionName, SchemaValidationErrorCollector errorCollector) {
        if (enumValueDefinitionName.length() >= 2 && enumValueDefinitionName.startsWith("__")) {
            SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.InvalidCustomizedNameError,
                    String.format("EnumValueDefinition \"%s\" in  \"%s\"  must not begin with \"__\", which is reserved by GraphQL introspection.", enumValueDefinitionName, typeName));
            errorCollector.addError(schemaValidationError);
        }
    }

    private void assertNonNullType(GraphQLType type, SchemaValidationErrorCollector errorCollector) {
        if (type instanceof GraphQLNonNull && ((GraphQLNonNull) type).getWrappedType() instanceof GraphQLNonNull) {
            SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.NonNullWrapNonNullError,
                    String.format("Non‐Null type must not wrap another Non‐Null type: \"%s\" is invalid.", GraphQLTypeUtil.simplePrint(type)));
            errorCollector.addError(schemaValidationError);
        }
    }

    private List<GraphQLNamedType> filterBuiltInTypes(List<GraphQLNamedType> graphQLNamedTypes) {
        if (graphQLNamedTypes == null || graphQLNamedTypes.isEmpty()) {
            return ImmutableKit.emptyList();
        }

        Predicate<GraphQLNamedType> filterFunction = namedType -> {
            if (isIntrospectionTypes(namedType)) {
                return false;
            }
            return !(namedType instanceof GraphQLScalarType) || !isGraphqlSpecifiedScalar((GraphQLScalarType) namedType);
        };

        return FpKit.filterList(graphQLNamedTypes, filterFunction);
    }


    private void assertFieldName(String typeName, String fieldName, SchemaValidationErrorCollector errorCollector) {
        if (INTROSPECTION_SYSTEM_FIELDS.contains(fieldName)) {
            return;
        }
        if (fieldName.length() >= 2 && fieldName.startsWith("__")) {
            SchemaValidationError schemaValidationError = new SchemaValidationError(SchemaValidationErrorType.InvalidCustomizedNameError,
                    String.format("\"%s\" in \"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", fieldName, typeName));
            errorCollector.addError(schemaValidationError);
        }
    }
}