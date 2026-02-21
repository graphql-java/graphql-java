package graphql.validation;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * Enumerates the individual validation rules that can be applied to a GraphQL operation document.
 * Each value corresponds to a validation rule defined in the GraphQL specification.
 *
 * <p>This enum is used with {@link OperationValidator} to selectively enable or disable
 * individual validation rules via a {@code Predicate<OperationValidationRule>}.
 *
 * <h2>Rule Categories by Traversal Behavior</h2>
 *
 * <p>The {@link OperationValidator} tracks two independent state variables during traversal:
 * {@code fragmentRetraversalDepth} (0 = primary traversal, >0 = inside fragment retraversal)
 * and {@code operationScope} (true = inside an operation, false = outside).
 *
 * <p>This creates three possible traversal states:
 * <pre>
 * ┌────────────────────────────────────┬──────────────────────────────────────────────────┐
 * │ State                              │ When                                             │
 * ├────────────────────────────────────┼──────────────────────────────────────────────────┤
 * │ depth=0, operationScope=false      │ Fragment definitions at document level           │
 * │ depth=0, operationScope=true       │ Nodes directly inside an operation               │
 * │ depth>0, operationScope=true       │ Nodes inside fragments reached via spread        │
 * └────────────────────────────────────┴──────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Rules are categorized by which states they run in:
 * <pre>
 * ┌──────────────────────┬──────────────────────┬─────────────────────┬─────────────────────┐
 * │ Rule Category        │ depth=0              │ depth=0             │ depth>0             │
 * │                      │ operationScope=false │ operationScope=true │ operationScope=true │
 * ├──────────────────────┼──────────────────────┼─────────────────────┼─────────────────────┤
 * │ Document-Level Rules │         RUN          │         RUN         │        SKIP         │
 * │ Operation-Scoped     │        SKIP          │         RUN         │         RUN         │
 * └──────────────────────┴──────────────────────┴─────────────────────┴─────────────────────┘
 * </pre>
 *
 * <h3>Document-Level Rules</h3>
 * <p>Condition: {@code fragmentRetraversalDepth == 0}
 * <p>Validates each AST node exactly once during primary traversal. Skips during fragment
 * retraversal to avoid duplicate errors (fragments are validated at document level).
 * <ul>
 *   <li>{@link #EXECUTABLE_DEFINITIONS} - only executable definitions allowed</li>
 *   <li>{@link #ARGUMENTS_OF_CORRECT_TYPE} - argument values match declared types</li>
 *   <li>{@link #FIELDS_ON_CORRECT_TYPE} - fields exist on parent type</li>
 *   <li>{@link #FRAGMENTS_ON_COMPOSITE_TYPE} - fragments on object/interface/union types</li>
 *   <li>{@link #KNOWN_ARGUMENT_NAMES} - arguments are defined on field/directive</li>
 *   <li>{@link #KNOWN_DIRECTIVES} - directives are defined in schema</li>
 *   <li>{@link #KNOWN_FRAGMENT_NAMES} - fragment spreads reference defined fragments</li>
 *   <li>{@link #KNOWN_TYPE_NAMES} - type references exist in schema</li>
 *   <li>{@link #NO_FRAGMENT_CYCLES} - fragments do not form cycles</li>
 *   <li>{@link #NO_UNUSED_FRAGMENTS} - all fragments are used by operations</li>
 *   <li>{@link #OVERLAPPING_FIELDS_CAN_BE_MERGED} - fields with same response key are mergeable</li>
 *   <li>{@link #POSSIBLE_FRAGMENT_SPREADS} - fragment type conditions overlap with parent</li>
 *   <li>{@link #PROVIDED_NON_NULL_ARGUMENTS} - required arguments are provided</li>
 *   <li>{@link #SCALAR_LEAVES} - scalar fields have no subselections, composites require them</li>
 *   <li>{@link #VARIABLE_DEFAULT_VALUES_OF_CORRECT_TYPE} - variable defaults match type</li>
 *   <li>{@link #VARIABLES_ARE_INPUT_TYPES} - variables use input types only</li>
 *   <li>{@link #LONE_ANONYMOUS_OPERATION} - anonymous operations are alone in document</li>
 *   <li>{@link #UNIQUE_OPERATION_NAMES} - operation names are unique</li>
 *   <li>{@link #UNIQUE_FRAGMENT_NAMES} - fragment names are unique</li>
 *   <li>{@link #UNIQUE_DIRECTIVE_NAMES_PER_LOCATION} - non-repeatable directives appear once</li>
 *   <li>{@link #UNIQUE_ARGUMENT_NAMES} - argument names are unique per field/directive</li>
 *   <li>{@link #UNIQUE_VARIABLE_NAMES} - variable names are unique per operation</li>
 *   <li>{@link #SUBSCRIPTION_UNIQUE_ROOT_FIELD} - subscriptions have single root field</li>
 *   <li>{@link #UNIQUE_OBJECT_FIELD_NAME} - input object fields are unique</li>
 *   <li>{@link #DEFER_DIRECTIVE_LABEL} - defer labels are unique strings</li>
 *   <li>{@link #KNOWN_OPERATION_TYPES} - schema supports the operation type</li>
 * </ul>
 *
 * <h3>Operation-Scoped Rules</h3>
 * <p>Condition: {@code operationScope == true}
 * <p>Tracks state across an entire operation, following fragment spreads to see all code paths.
 * Skips outside operations (e.g., fragment definitions at document level) where there is no
 * operation context to validate against.
 * <ul>
 *   <li>{@link #NO_UNDEFINED_VARIABLES} - all variable references are defined in operation</li>
 *   <li>{@link #NO_UNUSED_VARIABLES} - all defined variables are used somewhere</li>
 *   <li>{@link #VARIABLE_TYPES_MATCH} - variable types match usage location types</li>
 *   <li>{@link #DEFER_DIRECTIVE_ON_ROOT_LEVEL} - defer not on mutation/subscription root</li>
 *   <li>{@link #DEFER_DIRECTIVE_ON_VALID_OPERATION} - defer not in subscriptions</li>
 * </ul>
 *
 * <p>See {@link OperationValidator} class documentation for a detailed traversal example.
 *
 * @see OperationValidator
 */
@PublicApi
@NullMarked
public enum OperationValidationRule {

    /** Only executable definitions (operations and fragments) are allowed. */
    EXECUTABLE_DEFINITIONS,

    /** Argument values must be compatible with their declared types. */
    ARGUMENTS_OF_CORRECT_TYPE,

    /** Fields must exist on the parent type. */
    FIELDS_ON_CORRECT_TYPE,

    /** Fragment type conditions must be on composite types (object, interface, union). */
    FRAGMENTS_ON_COMPOSITE_TYPE,

    /** Arguments must be defined on the field or directive. */
    KNOWN_ARGUMENT_NAMES,

    /** Directives must be defined in the schema and used in valid locations. */
    KNOWN_DIRECTIVES,

    /** Fragment spreads must reference defined fragments. */
    KNOWN_FRAGMENT_NAMES,

    /** Type references must exist in the schema. */
    KNOWN_TYPE_NAMES,

    /** Fragments must not form cycles through spreads. */
    NO_FRAGMENT_CYCLES,

    /** All defined fragments must be used by at least one operation. */
    NO_UNUSED_FRAGMENTS,

    /** Fields with the same response key must be mergeable. */
    OVERLAPPING_FIELDS_CAN_BE_MERGED,

    /** Fragment type conditions must overlap with the parent type. */
    POSSIBLE_FRAGMENT_SPREADS,

    /** Required (non-null without default) arguments must be provided. */
    PROVIDED_NON_NULL_ARGUMENTS,

    /** Scalar fields must not have subselections; composite fields must have them. */
    SCALAR_LEAVES,

    /** Variable default values must match the variable type. */
    VARIABLE_DEFAULT_VALUES_OF_CORRECT_TYPE,

    /** Variables must be declared with input types (scalars, enums, input objects). */
    VARIABLES_ARE_INPUT_TYPES,

    /** Anonymous operations must be the only operation in the document. */
    LONE_ANONYMOUS_OPERATION,

    /** Operation names must be unique within the document. */
    UNIQUE_OPERATION_NAMES,

    /** Fragment names must be unique within the document. */
    UNIQUE_FRAGMENT_NAMES,

    /** Non-repeatable directives must appear at most once per location. */
    UNIQUE_DIRECTIVE_NAMES_PER_LOCATION,

    /** Argument names must be unique within a field or directive. */
    UNIQUE_ARGUMENT_NAMES,

    /** Variable names must be unique within an operation. */
    UNIQUE_VARIABLE_NAMES,

    /** Subscriptions must have exactly one root field (not introspection). */
    SUBSCRIPTION_UNIQUE_ROOT_FIELD,

    /** Input object field names must be unique. */
    UNIQUE_OBJECT_FIELD_NAME,

    /** Defer directive labels must be unique static strings. */
    DEFER_DIRECTIVE_LABEL,

    /** The schema must support the operation type (query/mutation/subscription). */
    KNOWN_OPERATION_TYPES,

    /** All variable references must be defined in the operation. Requires fragment traversal. */
    NO_UNDEFINED_VARIABLES,

    /** All defined variables must be used somewhere in the operation. Requires fragment traversal. */
    NO_UNUSED_VARIABLES,

    /** Variable types must be compatible with usage location types. Requires fragment traversal. */
    VARIABLE_TYPES_MATCH,

    /** Defer directive must not be on mutation or subscription root level. Requires operation context. */
    DEFER_DIRECTIVE_ON_ROOT_LEVEL,

    /** Defer directive must not be used in subscription operations. Requires operation context. */
    DEFER_DIRECTIVE_ON_VALID_OPERATION,
}
