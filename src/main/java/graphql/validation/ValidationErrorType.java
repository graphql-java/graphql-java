package graphql.validation;


public enum ValidationErrorType {

    DefaultForNonNullArgument,
    WrongType,
    UnknownType,
    SubSelectionRequired,
    SubSelectionNotAllowed,
    InvalidSyntax,
    BadValueForDefaultArg,
    FieldUndefined,
    InlineFragmentTypeConditionInvalid,
    FragmentTypeConditionInvalid

}
