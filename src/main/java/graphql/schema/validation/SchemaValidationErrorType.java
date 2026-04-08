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
    InvalidDirectiveDefinition,
    OutputTypeUsedInInputTypeContext,
    InputTypeUsedInOutputTypeContext,
    OneOfDefaultValueOnField,
    OneOfNonNullableField,
    OneOfNotInhabited,
    RequiredInputFieldCannotBeDeprecated,
    RequiredFieldArgumentCannotBeDeprecated,
    RequiredDirectiveArgumentCannotBeDeprecated
}
