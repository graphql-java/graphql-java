package graphql.schema.validation;

import graphql.Internal;

@Internal
public enum SchemaValidationErrorType implements SchemaValidationErrorClassification{

    UnbrokenInputCycle,
    ObjectDoesNotImplementItsInterfaces,
    ImplementingTypeLackOfFieldError,
    InputObjectTypeLackOfFieldError,
    EnumLackOfValueError,
    UnionTypeLackOfTypeError,
    InvalidUnionMemberTypeError,
    InvalidCustomizedNameError,
    NonNullWrapNonNullError,
    RepetitiveElementError,
    InvalidDefaultValue,
    InvalidAppliedDirectiveArgument,
    InvalidAppliedDirective,
    OutputTypeUsedInInputTypeContext,
    InputTypeUsedInOutputTypeContext,
    OneOfDefaultValueOnField,
    OneOfNonNullableField,
    RequiredInputFieldCannotBeDeprecated,
    RequiredFieldArgumentCannotBeDeprecated,
    RequiredDirectiveArgumentCannotBeDeprecated,
    DefaultValueCircularRef
}
