package graphql.validation;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * Enumerates the individual validation rules that can be applied to a GraphQL operation document.
 * Each value corresponds to a validation rule defined in the GraphQL specification.
 *
 * <p>This enum is used with {@link OperationValidator} to selectively enable or disable
 * individual validation rules via a {@code Predicate<OperationValidationRule>}.
 */
@PublicApi
@NullMarked
public enum OperationValidationRule {
    EXECUTABLE_DEFINITIONS,
    ARGUMENTS_OF_CORRECT_TYPE,
    FIELDS_ON_CORRECT_TYPE,
    FRAGMENTS_ON_COMPOSITE_TYPE,
    KNOWN_ARGUMENT_NAMES,
    KNOWN_DIRECTIVES,
    KNOWN_FRAGMENT_NAMES,
    KNOWN_TYPE_NAMES,
    NO_FRAGMENT_CYCLES,
    NO_UNDEFINED_VARIABLES,
    NO_UNUSED_FRAGMENTS,
    NO_UNUSED_VARIABLES,
    OVERLAPPING_FIELDS_CAN_BE_MERGED,
    POSSIBLE_FRAGMENT_SPREADS,
    PROVIDED_NON_NULL_ARGUMENTS,
    SCALAR_LEAVES,
    VARIABLE_DEFAULT_VALUES_OF_CORRECT_TYPE,
    VARIABLES_ARE_INPUT_TYPES,
    VARIABLE_TYPES_MATCH,
    LONE_ANONYMOUS_OPERATION,
    UNIQUE_OPERATION_NAMES,
    UNIQUE_FRAGMENT_NAMES,
    UNIQUE_DIRECTIVE_NAMES_PER_LOCATION,
    UNIQUE_ARGUMENT_NAMES,
    UNIQUE_VARIABLE_NAMES,
    SUBSCRIPTION_UNIQUE_ROOT_FIELD,
    UNIQUE_OBJECT_FIELD_NAME,
    DEFER_DIRECTIVE_ON_ROOT_LEVEL,
    DEFER_DIRECTIVE_ON_VALID_OPERATION,
    DEFER_DIRECTIVE_LABEL,
    KNOWN_OPERATION_TYPES,
    GOOD_FAITH_INTROSPECTION,
}
