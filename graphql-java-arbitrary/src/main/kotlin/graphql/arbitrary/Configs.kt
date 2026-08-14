package graphql.arbitrary

import graphql.language.Value
import io.kotest.property.Arb

/**
 * The approximate number of types and directives that a generated schema will define.
 * Due to name collisions and the potential addition of built-in scalars, the generated
 * schemas may contain slightly more or less than the configured amount.
 */
object SchemaSize : ConfigKey<Int>(20, IntValidator(0..1_000_000))

/**
 * Relative weights of different [TypeKind]s. Any unset keys will be interpreted as a weight of 1.0.
 * Weights may be any positive double and may be greater than 1.0.
 */
object TypeKindWeights : ConfigKey<Map<TypeKind, Double>>(emptyMap(), Unvalidated) {
    /** A value with all [TypeKind]s configured with a 0 weight */
    val zero: Map<TypeKind, Double> = TypeKind.entries.associateWith { 0.0 }
}

/** The probability that an Object type will implement any interface */
object ObjectImplementsInterface : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.1, 10),
    CompoundingWeightValidator
)

/** The probability that an Interface type will implement another interface */
object InterfaceImplementsInterface : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.1, 3),
    CompoundingWeightValidator
)

/**
 * The probability that an interface or object field has arguments.
 * This is a compounding probability, meaning that if configured with value .1, then 10% of
 * fields will have at least 1 argument, 1% will have at least 2 arguments,
 * .1% at least 3, and so on.
 */
object FieldArgumentWeight : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.1, 3),
    CompoundingWeightValidator
)

/**
 * The probability that a GraphQL element will define a default value. This applies to
 * directive args, field args, input fields, variables, etc
 */
object DefaultValueWeight : ConfigKey<Double>(.2, WeightValidator)

/** Should the Schema define and use builtin scalar types (String, Int, etc) */
object IncludeBuiltinScalars : ConfigKey<Boolean>(true, Unvalidated)

/** Should the Schema generate and use arbitrary scalar types */
object GenCustomScalars : ConfigKey<Boolean>(false, Unvalidated)

/** Should the Schema define and use builtin directives (@oneOf, @deprecated, etc) */
object IncludeBuiltinDirectives : ConfigKey<Boolean>(true, Unvalidated)

/** The probability that a Directive will be repeatable */
object DirectiveIsRepeatable : ConfigKey<Double>(.1, WeightValidator)

/** The probability that a Directive will have arguments */
object DirectiveHasArgs : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.1, 3),
    CompoundingWeightValidator
)

/** The probability that a schema element or document node will have an applied directive */
object AppliedDirectiveWeight : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.1, 3),
    CompoundingWeightValidator
)

/**
 * Include the provided types in a generated schema. The included types will
 * be used in the type pool and may be used by other generated types in the schema.
 */
object IncludeTypes : ConfigKey<GraphQLTypes>(GraphQLTypes.empty, Unvalidated)

/**
 * The range of possible GraphQL name lengths for type-like GraphQL elements.
 * This includes schema type names, operation names, fragment names, etc.
 */
object TypeNameLength : ConfigKey<IntRange>(1..10, IntRangeValidator(1..Int.MAX_VALUE))

/**
 * The range of possible GraphQL name lengths for field-like GraphQL elements.
 * This includes object field names, input object field names, enum value names,
 * argument names, variable names, alias names, etc
 */
object FieldNameLength : ConfigKey<IntRange>(1..10, IntRangeValidator(1..Int.MAX_VALUE))

/**
 * The range of possible description string lengths.
 * As nearly all GraphQL types support descriptions, longer values
 * can significantly slow down tests.
 */
object DescriptionLength : ConfigKey<IntRange>(0..10, IntRangeValidator(0..Int.MAX_VALUE))

/** The probability that a field type will be wrapped in a List type */
object ListTypeWeight : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.1, 2),
    CompoundingWeightValidator
)

/** The probability that a field- or argument type will be non-nullable */
object NonNullableTypeWeight : ConfigKey<Double>(.2, WeightValidator)

/** The number of input fields that an input object type will define */
object InputObjectTypeSize : ConfigKey<IntRange>(1..3, IntRangeValidator(1..Int.MAX_VALUE))

/** The probability that a generated input object will be a OneOf type */
object OneOfTypeWeight : ConfigKey<Double>(.25, WeightValidator)

/**
 * The number of fields that an interface type will define.
 * Interfaces that implement other interfaces may define more than the maximum configured amount.
 */
object InterfaceTypeSize : ConfigKey<IntRange>(1..3, IntRangeValidator(1..Int.MAX_VALUE))

/**
 * The number of fields that an object type will define
 * Objects that implement interfaces may define more than the maximum configured amount
 */
object ObjectTypeSize : ConfigKey<IntRange>(1..3, IntRangeValidator(1..Int.MAX_VALUE))

/** The number of members that a union type will include */
object UnionTypeSize : ConfigKey<IntRange>(1..3, IntRangeValidator(1..Int.MAX_VALUE))

/** The number of values that an enum type will define */
object EnumTypeSize : ConfigKey<IntRange>(1..3, IntRangeValidator(1..Int.MAX_VALUE))

/**
 * For fields that support it, the probability that a generated GraphQL value will be
 * implicitly null (ie the field key will not be included in the value map).
 *
 * For input fields and arguments, this is applicable when a field type is nullable
 * or has a default value.
 * For output fields, this is applicable for all field types.
 */
object ImplicitNullValueWeight : ConfigKey<Double>(.1, WeightValidator)

/**
 * For types that support it, the probability that a generated GraphQL value will
 * be explicitly null (ie value == `null`).
 *
 * This is applicable for any type that is nullable.
 */
object ExplicitNullValueWeight : ConfigKey<Double>(.1, WeightValidator)

/** The range of lengths of generated GraphQL list values */
object ListValueSize : ConfigKey<IntRange>(0..3, IntRangeValidator(0..Int.MAX_VALUE))

/**
 * The probability that a value literal in a schema, when allowed, will require coercion to match the type at its location.
 *
 * Examples of allowed uncoerced values include:
 * - ID-typed values may be provided as Int literals
 * - Float-typed values may be provided as Int literals
 * - Some List-typed values may be provided as the unwrapped list type
 */
object SchemaUncoercedValueWeight : ConfigKey<Double>(.25, WeightValidator)

/**
 * The probability that a value literal in a document, when allowed, will require coercion to match the type at its location.
 *
 * Examples of allowed uncoerced values include:
 * - ID-typed values may be provided as Int literals
 * - Float-typed values may be provided as Int literals
 * - Some List-typed values may be provided as the unwrapped list type
 */
object DocumentUncoercedValueWeight : ConfigKey<Double>(.25, WeightValidator)

/**
 * The approximate maximum depth of attempted value generation. When generating
 * values past this depth, the value generator will return null or empty values
 * when possible.
 */
object MaxValueDepth : ConfigKey<Int>(3, IntValidator(0..Int.MAX_VALUE))

/** The range of lengths of generated GraphQL string values */
object StringValueSize : ConfigKey<IntRange>(0..3, IntRangeValidator(0..Int.MAX_VALUE))

/**
 * If enabled, all interface definitions will be guaranteed to have at least one
 * implementing type in the schema.
 *
 * This can be useful for systems that want to require that a value can be produced
 * for every output type in a schema.
 */
object GenInterfaceStubsIfNeeded : ConfigKey<Boolean>(false, Unvalidated)

/**
 * Reject the configured names from being used in the generated schema as
 * input fields, output fields, argument names, enum values, and other field-ish
 * contexts.
 *
 * This can be useful for ensuring that a schema is compatible with code generators
 * that may not use language keywords as identifiers.
 */
object BanFieldNames : ConfigKey<Set<String>>(emptySet(), Unvalidated)

/** ban the configured directives from generated schemas and documents */
object BanDirectiveNames : ConfigKey<Set<String>>(emptySet(), Unvalidated)

/**
 * Use the provided mappings for generating scalar value literals,
 * on top of the generators for builtin GraphQL scalar types.
 */
object ScalarLiteralOverrides : ConfigKey<Map<String, Arb<Value<*>>>>(
    emptyMap(),
    Unvalidated
)

/**
 * Use the provided mappings for generating external scalar values,
 * on top of the generators for builtin GraphQL scalar types.
 */
object ScalarValueOverrides : ConfigKey<Map<String, Arb<Any?>>>(emptyMap(), Unvalidated)

/** The factory to use when generating native String values for GraphQL ID scalars. */
object IDValueGenFactory : ConfigKey<IDValueGen.Factory>(IDValueGen.Factory.default, Unvalidated)

/** probability that a selection set will contain field selections that are not wrapped in an inline fragment or a named fragment spread */
object FieldSelectionWeight : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.4, 5),
    CompoundingWeightValidator
)

/** probability that a selection set will contain an inline fragment */
object InlineFragmentWeight : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.4, 3),
    CompoundingWeightValidator
)

/** probability that a selection set will spread a named fragment */
object FragmentSpreadWeight : ConfigKey<CompoundingWeight>(
    CompoundingWeight(.4, 3),
    CompoundingWeightValidator
)

/** probability that a fragment spread will spread a new fragment definition, rather than an existing one */
object FragmentDefinitionWeight : ConfigKey<Double>(.2, WeightValidator)

/** probability that, where possible, an inline fragment will have no type condition */
object UntypedInlineFragmentWeight : ConfigKey<Double>(.2, WeightValidator)

/**
 * The maximum depth of a selection set.
 * Each inline fragment, fragment spread, and field subselections will increment depth by 1
 */
object MaxSelectionSetDepth : ConfigKey<Int>(10, IntValidator(0..100))

/** probability that any given selection will be aliased */
object AliasWeight : ConfigKey<Double>(.2, WeightValidator)

/** The range of how many operation definitions may be generated in a Document */
object OperationCount : ConfigKey<IntRange>(1..2, IntRangeValidator(1..Int.MAX_VALUE))

/**
 * Where possible, the probability that an operation definition will not have a name,
 * or that an ExecutionInput will omit an operation name.
 */
object AnonymousOperationWeight : ConfigKey<Double>(.5, WeightValidator)

/**
 * probability that any given input value or part of an input value will be
 * replaced by a variable.
 */
object VariableWeight : ConfigKey<Double>(.3, WeightValidator)

/** probability that a graphql-java DataFetcher will return null for a non-nullable field */
object NullNonNullableWeight : ConfigKey<Double>(0.0, WeightValidator)

/**
 * probability that a graphql-java DataFetcher or TypeResolver will throw a
 * RuntimeException during execution.
 */
object ResolverExceptionWeight : ConfigKey<Double>(0.0, WeightValidator)

/**
 * The likelihood that when generating a concrete value for an abstract type, that
 * the generator will pick a type that is selected in the selection set rather than
 * any possible implementing type.
 */
object SelectedTypeBias : ConfigKey<Double>(.9, WeightValidator)

/** A schema type coordinate paired with an optional field name. */
typealias TypeOrFieldCoordinate = Pair<String, String?>

/** A set of [TypeOrFieldCoordinate]s that a generator may not generate selections for. */
object BanSelectionCoordinates : ConfigKey<Set<TypeOrFieldCoordinate>>(emptySet(), Unvalidated)
