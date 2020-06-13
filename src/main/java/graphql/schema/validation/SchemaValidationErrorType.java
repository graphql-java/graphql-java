package graphql.schema.validation;

public enum SchemaValidationErrorType {

    UnbrokenInputCycle,
    ObjectDoesNotImplementItsInterfaces,
    ObjectTypeLackOfFieldError,
    InterfaceLackOfFieldError,
    InputObjectTypeLackOfFieldError,
    EnumLackOfValueError,
    UnionTypeLackOfTypeError,
    InvalidUnionMemberTypeError,
    InvalidCustomizedNameError,
    NonNullWrapNonNullError,
    RepetitiveElementError

}
