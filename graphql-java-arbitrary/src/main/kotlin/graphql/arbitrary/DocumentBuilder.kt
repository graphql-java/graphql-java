package graphql.arbitrary

import graphql.language.Argument
import graphql.language.AstPrinter
import graphql.language.Directive
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.TypeName
import graphql.language.Value
import graphql.language.VariableDefinition
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import graphql.validation.VariablesTypesMatcher

private typealias GJSelection = graphql.language.Selection<*>

internal class Fragments(
    private val schemas: Schemas,
    private val fragmentsByName: MutableMap<String, FragmentDef> = mutableMapOf(),
    private val fragmentsByType: MutableMap<GraphQLCompositeType, MutableList<FragmentDef>> = mutableMapOf(),
) : Schemas by schemas {
    val fragments: List<FragmentDef> get() = fragmentsByName.values.toList()

    operator fun get(key: String): FragmentDef? = fragmentsByName[key]

    fun spreadableFragments(
        sb: SelectionsBuilder,
        type: GraphQLCompositeType
    ): List<FragmentDef> =
        fragmentsByType
            .flatMap { (k, v) ->
                if (!schemas.rels.isSpreadable(type, k)) emptyList() else v
            }.filter { fb ->
                fb.sb.fieldSelections().all(sb::canAdd)
            }

    fun add(fragment: FragmentDef) {
        require(get(fragment.name) == null) {
            "cannot overwrite fragment with name `${fragment.name}"
        }
        fragmentsByName += (fragment.name to fragment)

        val fragType = requireNotNull(
            schemas.schema.getTypeAs<GraphQLCompositeType>(fragment.typeCondition.name)
        )
        fragmentsByType.getOrPut(fragType) { mutableListOf() } += fragment
    }

    operator fun plusAssign(fragment: FragmentDef) = add(fragment)
}

internal class Variables(
    private val schemas: Schemas,
    private val variablesByName: MutableMap<String, VariableDefinition> = mutableMapOf(),
) {
    private val matcher = VariablesTypesMatcher()

    val variables: List<VariableDefinition> get() = variablesByName.values.toList()

    operator fun get(name: String): VariableDefinition? = variablesByName[name]

    /** Get any variables that can be validly applied at a given location */
    fun variablesForType(
        locationType: GraphQLInputType,
        locationDefault: Value<*>?
    ): List<VariableDefinition> =
        variables.filter { v ->
            val vtype = v.type.asSchemaType(schemas.schema)

            // Consider this valid schema and document
            //   schema:    type Query { x(a:Int!=0) }
            //   doc:       query ($a:Int) { x(a:$a) }
            // This doc uses $a, which may be null, in a position that requires a non-nullable value.
            // This is considered valid by the spec (see IsVariableUsageAllowed), though a
            // validation error will be raised at runtime if the value of $a is explicitly null.
            //
            // It would be nice to support this edge case, though doing so is somewhat difficult.
            // Let's disallow this kind of variable reuse.
            val nullableUsedNonNullably =
                GraphQLTypeUtil.isNullable(vtype) && GraphQLTypeUtil.isNonNull(locationType)

            if (nullableUsedNonNullably) {
                false
            } else {
                matcher.doesVariableTypesMatch(
                    vtype,
                    v.defaultValue,
                    locationType,
                    locationDefault
                )
            }
        }

    fun add(variable: VariableDefinition) {
        val name = requireNotNull(variable.name)
        require(!variablesByName.containsKey(name)) {
            "cannot overwrite variable with name `$name`"
        }
        variablesByName[name] = variable
    }
}

/**
 * Accumulator for label strings used in @defer and @stream.
 * Labels must be unique per document.
 */
internal class IncrementalLabels(
    private val set: MutableSet<String> = mutableSetOf()
) {
    fun contains(label: String) = label in set

    fun add(label: String) {
        set += label
    }
}

internal sealed interface Selection {
    /**
     * Return the field selections provided by this [Selection].
     * This is non-recursive and should not include sub selections of fields.
     */
    fun flatten(): List<FieldSelection>

    /** the graphql-java representation of this Selection */
    val gjSelection: GJSelection
}

internal class FieldSelection private constructor(
    val key: FieldKey,
    override val gjSelection: Field,
    /** the merge tree of this Fields subselections */
    val mergeKeyTree: KeyTree?
) : Selection {
    override fun flatten(): List<FieldSelection> = listOf(this)

    companion object {
        operator fun invoke(
            key: FieldKey,
            selections: SelectionsBuilder? = null,
            directives: List<Directive> = emptyList()
        ): FieldSelection {
            val field = Field
                .newField()
                .name(key.fieldName)
                .alias(key.alias)
                .arguments(
                    key.arguments.map { it.arg }.toList()
                ).selectionSet(selections?.build())
                .directives(directives)
                .build()
            return FieldSelection(key, field, selections?.mergeKeyTree)
        }
    }
}

internal data class InlineFragmentSelection(
    override val gjSelection: InlineFragment,
    private val selections: List<Selection>,
) : Selection {
    constructor(
        sdlTypeCondition: String? = null,
        selections: List<Selection> = emptyList(),
        directives: List<Directive> = emptyList()
    ) : this(
        InlineFragment
            .newInlineFragment()
            .typeCondition(sdlTypeCondition?.let(::TypeName))
            .selectionSet(SelectionsBuilder(selections).build())
            .directives(directives)
            .build(),
        selections,
    )

    override fun flatten(): List<FieldSelection> = selections.flatMap { it.flatten() }
}

internal data class FragmentSpreadSelection(
    override val gjSelection: FragmentSpread,
    /** the selections of the fragment definition */
    private val selections: List<Selection>
) : Selection {
    constructor(
        name: String,
        selections: List<Selection>,
        directives: List<Directive> = emptyList()
    ) : this(
        FragmentSpread
            .newFragmentSpread(name)
            .directives(directives)
            .build(),
        selections
    )

    override fun flatten(): List<FieldSelection> = selections.flatMap { it.flatten() }
}

internal data class DocumentBuilder(
    val schemas: Schemas,
    val fragments: Fragments = Fragments(schemas),
    // the draft version of the graphql-spec requires that labels are unique per operation,
    // though graphql-java requires that they are unique per-document.
    // We'll follow the GJ behavior here
    val incrementalLabels: IncrementalLabels = IncrementalLabels(),
    val operations: MutableList<OperationDefinition> = mutableListOf()
) : Schemas by schemas {
    fun add(operationDefinition: OperationDefinition) {
        require(operations.none { it.name == operationDefinition.name }) {
            "Cannot overwrite operation with name `${operationDefinition.name}`"
        }
        operations += operationDefinition
    }

    fun build(): Document = Document(operations + fragments.fragments.map { it.def })
}

@ConsistentCopyVisibility
internal data class ArgumentKey private constructor(
    private val repr: String,
    val arg: Argument
) {
    constructor(arg: Argument) : this(AstPrinter.printAstCompact(arg), arg)

    override fun toString(): String = repr

    override fun equals(other: Any?): Boolean = repr == (other as? ArgumentKey)?.repr

    override fun hashCode(): Int = repr.hashCode()
}

internal data class FieldKey(
    val fieldName: String,
    val alias: String?,
    val arguments: Set<ArgumentKey>,
    val fieldType: TypeExpr
) {
    val resultKey: String get() = alias ?: fieldName

    override fun toString(): String =
        buildString {
            if (alias != null) {
                append(alias)
                append(':')
            }
            append(fieldName)
            if (arguments.isNotEmpty()) {
                append('(')
                append(arguments.map { it.toString() }.joinToString(","))
                append(')')
            }
            append("  type=")
            append(fieldType.name)
        }
}

/**
 * TypeExpr describes a GraphQL type and its [GraphQLNonNull] and [GraphQLList]
 * type wrappers.
 *
 * Type wrappers are converted into a distinct hash value, [listNullHash].
 * This hash allows comparing that two types are equal, where equality is defined as
 * referential equality of the unwrapped type and structural equality of the type wrappers.
 */
@ConsistentCopyVisibility
internal data class TypeExpr private constructor(
    val type: GraphQLNamedType,
    val listNullHash: Int,
) {
    val name: String = type.name

    companion object {
        operator fun invoke(type: GraphQLOutputType): TypeExpr {
            // build an integer hash that represents the wrappers on the provided type.
            // The rules are:
            //   - the initial value will be 1
            //   - the value will be left-shifted by 1 for every wrapper
            //   - the value will be incremented by 1 when the wrapper is non-null
            //
            // Examples:
            //   Int     -> no transformations      -> 1
            //   [Int]   -> (1 shl 1)               -> 2
            //   Int!    -> (1 shl 1) + 1           -> 3
            //   [Int]!  -> ((1 shl 1) + 1) shl 1   -> 5
            tailrec fun loop(
                acc: Int,
                type: GraphQLType
            ): TypeExpr =
                when (type) {
                    is GraphQLList -> loop(acc shl 1, type.wrappedType)
                    is GraphQLNonNull -> loop((acc shl 1) + 1, type.wrappedType)
                    is GraphQLNamedType -> TypeExpr(type, acc)
                    else -> throw IllegalArgumentException("Unsupported type $type")
                }
            return loop(1, type)
        }
    }
}

internal fun GraphQLFieldDefinition.key(
    alias: String? = null,
    arguments: Set<Argument> = emptySet()
): FieldKey = FieldKey(name, alias, arguments.map(::ArgumentKey).toSet(), TypeExpr(type))

internal data class FragmentDef(
    val name: String,
    val typeCondition: GraphQLCompositeType,
    val def: FragmentDefinition,
    val sb: SelectionsBuilder,
    val variables: List<VariableDefinition>,
) {
    companion object {
        operator fun invoke(
            name: String,
            typeCondition: GraphQLCompositeType,
            sb: SelectionsBuilder,
            variables: List<VariableDefinition>,
            directives: List<Directive>,
        ): FragmentDef {
            val def = FragmentDefinition
                .newFragmentDefinition()
                .name(name)
                .typeCondition(TypeName(typeCondition.name))
                .selectionSet(sb.build())
                .directives(directives)
                .build()
            return FragmentDef(name, typeCondition, def, sb, variables)
        }
    }
}

/** A shared mutable view of merged fields in a selection set */
internal class KeyTree private constructor(
    /**
     * `map` models the merged tree of a selection set, where each key
     * in the map describes a field and the value of that key describes its
     * subselections.
     * A null value indicates that a field will never have subselections, while
     * a non-null value describes the subselections that may be complete or incompletely
     * assembled.
     */
    private val map: MutableMap<FieldKey, KeyTree?>,
    private val resultNameToKey: MutableMap<String, FieldKey>
) {
    constructor() : this(mutableMapOf(), mutableMapOf())

    operator fun get(key: FieldKey): KeyTree? = map[key]

    fun toMap(): Map<FieldKey, Map<FieldKey, Any?>?> = map.mapValues { it.value?.toMap() }

    fun isEmpty(): Boolean = map.isEmpty()

    val keys: Set<FieldKey> get() = map.keys.toSet()

    fun clone(): KeyTree {
        val new = KeyTree()
        new.merge(this)
        return new
    }

    fun checkCycles() {
        // check tree for cyclic references, which can cause infinite
        // loops while merging
        fun loop(
            path: List<KeyTree>,
            tree: KeyTree
        ) {
            check(tree !in path) {
                "cycle detected"
            }
            tree.map.values.forEach {
                if (it != null) {
                    loop(path + tree, it)
                }
            }
        }
        loop(emptyList(), this)
    }

    /** merge the provided entry into this [KeyTree] */
    fun merge(
        key: FieldKey,
        value: KeyTree?
    ) {
        if (map.contains(key)) {
            val extant = map[key]
            checkValueTypes(extant, value)
            extant?.merge(requireNotNull(value))
        } else {
            write(key, value)
        }
    }

    /** merge the provided [KeyTree] into this object */
    fun merge(other: KeyTree): KeyTree {
        other.map.forEach { (k, v) ->
            if (k in map) {
                val extant = map[k]
                checkValueTypes(extant, v)
                extant?.merge(requireNotNull(v))
            } else {
                write(k, v)
            }
        }
        return this
    }

    private fun write(
        key: FieldKey,
        value: KeyTree?
    ) {
        check(canMerge(key)) {
            "Cannot overwrite extant key ${resultNameToKey[key.resultKey]} for result `${key.resultKey}` with $key"
        }
        map[key] = value?.clone()
        resultNameToKey[key.resultKey] = key
    }

    fun canMerge(key: FieldKey): Boolean = resultNameToKey[key.resultKey]?.let { it == key } ?: true

    fun canMerge(
        key: FieldKey,
        value: KeyTree?
    ): Boolean {
        if (!canMerge(key)) return false
        if (map.containsKey(key)) {
            val extant = map[key]
            checkValueTypes(extant, value)
            if (extant != null) {
                return extant.canMerge(requireNotNull(value))
            }
        }
        return true
    }

    fun canMerge(other: KeyTree): Boolean =
        other.map.all { (k, v) ->
            if (!canMerge(k)) {
                false
            } else if (map.contains(k)) {
                val extant = map[k]
                checkValueTypes(extant, v)
                extant?.canMerge(requireNotNull(v)) ?: true
            } else {
                true
            }
        }

    /** check that both of the provided <KeyTree>'s support or don't support sub selections */
    private fun checkValueTypes(
        extant: KeyTree?,
        value: KeyTree?
    ) {
        require((extant == null) == (value == null)) {
            "mismatched value types"
        }
    }

    companion object {
        /**
         * Initialize a new [KeyTree] that for the provided key
         * The returned KeyTree will model the object that contains [key]
         */
        operator fun invoke(key: FieldKey): KeyTree {
            val value = if (key.fieldType.type is GraphQLCompositeType) {
                KeyTree()
            } else {
                null
            }
            return KeyTree().also { it.merge(key, value) }
        }
    }
}

/**
 * A SelectionsBuilder assembles a selection set that is capable of being merged
 * according to the rules of GraphQL field selection merging, described in detail at
 *   https://spec.graphql.org/draft/#sec-Field-Selection-Merging
 */
internal class SelectionsBuilder private constructor(
    private val mutableSelections: MutableList<Selection>,
    /**
     * A tree of fields that this builder is merge-compatible with.
     * This tree includes all keys provided by [mutableSelections]
     */
    val mergeKeyTree: KeyTree,
) {
    companion object {
        fun buildKeyTree(f: FieldSelection): KeyTree =
            KeyTree().also {
                it.merge(f.key, f.mergeKeyTree)
            }

        operator fun invoke(localSelections: List<Selection> = emptyList()): SelectionsBuilder {
            val mergeKeyTree = localSelections
                .flatMap { it.flatten() }
                .map(::buildKeyTree)
                .fold(KeyTree()) { acc, other -> acc.merge(other) }

            return SelectionsBuilder(
                localSelections.toMutableList(),
                mergeKeyTree
            )
        }
    }

    /** return true if this SelectionsBuilder contains no selections */
    val isEmpty: Boolean get() = mutableSelections.isEmpty()

    val selections: List<Selection> get() = mutableSelections.toList()

    /**
     * return true if the provided FieldKey can be added to this [SelectionsBuilder] without
     * violating the rules of field mergeability.
     *
     * https://spec.graphql.org/draft/#sec-Field-Selection-Merging
     */
    fun canAdd(key: FieldKey): Boolean = mergeKeyTree.canMerge(KeyTree(key))

    /**
     * return true if every field selection in [selection] could be
     * added to this object without violating the rules of field mergeability.
     *
     * https://spec.graphql.org/draft/#sec-Field-Selection-Merging
     */
    fun canAdd(selection: Selection): Boolean =
        selection.flatten().all { s ->
            mergeKeyTree.canMerge(s.key, s.mergeKeyTree)
        }

    /**
     * Return the fields that are locally selected by this [SelectionsBuilder].
     * This effectively flattens all inline fragments and fragment spreads.
     */
    fun fieldSelections(): List<FieldSelection> = selections.flatMap { it.flatten() }

    /**
     * Add the provided [Selection] to this [SelectionsBuilder].
     * This will throw if [selection] contains fields that are not addable.
     * @see canAdd
     */
    fun add(selection: Selection) {
        // sanity check
        require(canAdd(selection)) {
            "add called with an un-addable selection"
        }

        // add all fields within selection to the merge scope
        selection.flatten().forEach { field ->
            mergeKeyTree.merge(field.key, field.mergeKeyTree)
        }
        mergeKeyTree.checkCycles()
        mutableSelections += selection
    }

    /**
     * Create a new [SelectionsBuilder] that models an InlineFragment or FragmentSpread.
     * The new builder will retain a view of this builder's view on merging.
     * @see canAdd
     */
    fun newSpreadScope(): SelectionsBuilder =
        SelectionsBuilder(
            mutableSelections = mutableListOf(),
            mergeKeyTree,
        )

    /** create a new scope suitable for building a fragment definition */
    fun newFragmentScope(): SelectionsBuilder = SelectionsBuilder(mutableSelections = mutableListOf(), mergeKeyTree.clone())

    /**
     * Create a new [SelectionsBuilder] that models the field selections for the provided [FieldKey]
     * The new builder will retain a view of this builder's view on field merging.
     * @see canAdd
     */
    fun newFieldScope(key: FieldKey): SelectionsBuilder =
        SelectionsBuilder(
            mutableSelections = mutableListOf(),
            mergeKeyTree = mergeKeyTree[key] ?: KeyTree(key)
        )

    fun build(): SelectionSet =
        SelectionSet(selections.map { it.gjSelection })
            .also {
                if (it.selections.isEmpty()) {
                    throw AssertionError("Sanity check failed: generated an empty selection set")
                }
            }
}
