package graphql.schema.validation.exception;

public enum SchemaValidationErrorType {
    UnbrokenInputCycle,
    ObjectDoesNotImplementItsInterfaces,
    GraphQLTypeError,
    GraphQLEnumError,
    GraphQLInterfaceError,
    GraphQLUnionTypeError,
    DirectiveInvalidError,
    FieldDefinitionError,
    InValidName
}
