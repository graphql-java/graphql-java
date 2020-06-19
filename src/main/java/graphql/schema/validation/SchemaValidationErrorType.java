package graphql.schema.validation;

public enum SchemaValidationErrorType {

    UnbrokenInputCycle,
    ObjectDoesNotImplementItsInterfaces,
    ImplementingTypeLackOfFieldError,
    InputObjectTypeLackOfFieldError,
    EnumLackOfValueError,
    UnionTypeLackOfTypeError,
    InvalidUnionMemberTypeError,
    InvalidCustomizedNameError,
    NonNullWrapNonNullError,
    RepetitiveElementError

}
