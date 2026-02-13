package graphql.validation;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
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
    VariableNotAllowed,
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
    DuplicateIncrementalLabel,
    DuplicateVariableName,
    NullValueForNonNullArgument,
    SubscriptionMultipleRootFields,
    SubscriptionIntrospectionRootField,
    UniqueObjectFieldName,
    UnknownOperation
}
