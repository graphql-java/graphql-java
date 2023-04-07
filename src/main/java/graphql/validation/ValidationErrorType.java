package graphql.validation;


import graphql.PublicApi;

@PublicApi
public enum ValidationErrorType implements ValidationErrorClassification {

    MaxValidationErrorsReached,
    DefaultForNonNullArgument,
    WrongType,
    UnknownType,
    SubselectionRequired,
    SubselectionNotAllowed,
    InvalidSyntax,
    BadValueForDefaultArg,
    FieldUndefined,
    InlineFragmentTypeConditionInvalid,
    FragmentTypeConditionInvalid,
    UnknownArgument,
    UndefinedFragment,
    NonInputTypeOnVariable,
    UnusedFragment,
    MissingFieldArgument,
    MissingDirectiveArgument,
    VariableTypeMismatch,
    UnknownDirective,
    MisplacedDirective,
    UndefinedVariable,
    UnusedVariable,
    FragmentCycle,
    FieldsConflict,
    InvalidFragmentType,
    LoneAnonymousOperationViolation,
    NonExecutableDefinition,
    DuplicateOperationName,
    DuplicateFragmentName,
    DuplicateDirectiveName,
    DuplicateArgumentNames,
    DuplicateVariableName,
    NullValueForNonNullArgument,
    SubscriptionMultipleRootFields,
    SubscriptionIntrospectionRootField,
    UniqueObjectFieldName
}
