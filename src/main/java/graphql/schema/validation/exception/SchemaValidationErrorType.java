package graphql.schema.validation.exception;

public enum SchemaValidationErrorType {

    UnbrokenInputCycle,
    ObjectDoesNotImplementItsInterfaces,
    DirectiveInvalidError
}
