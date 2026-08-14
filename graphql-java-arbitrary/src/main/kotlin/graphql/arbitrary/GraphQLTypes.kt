package graphql.arbitrary

import graphql.Directives
import graphql.Scalars
import graphql.introspection.Introspection.DirectiveLocation
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import graphql.schema.GraphQLUnmodifiedType
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import kotlin.math.min

/** A bag of generated types that can be converted into a GraphQLSchema */
data class GraphQLTypes(
    val interfaces: Map<String, GraphQLInterfaceType>,
    val objects: Map<String, GraphQLObjectType>,
    val inputs: Map<String, GraphQLInputObjectType>,
    val unions: Map<String, GraphQLUnionType>,
    val scalars: Map<String, GraphQLScalarType>,
    val enums: Map<String, GraphQLEnumType>,
    val directives: Map<String, GraphQLDirective>
) {
    val names: Set<String> by lazy {
        val s = mutableSetOf<String>()
        s += interfaces.keys
        s += objects.keys
        s += inputs.keys
        s += unions.keys
        s += scalars.keys
        s += enums.keys
        s += directives.keys
        s.toSet()
    }

    /** Try to resolve a GraphQLTypeReference into a GraphQLType */
    fun resolve(ref: GraphQLTypeReference): GraphQLUnmodifiedType? {
        ref.name.let { name ->
            listOf(interfaces, objects, inputs, unions, scalars, enums).forEach {
                if (it.contains(name)) return it[name]
            }
            return null
        }
    }

    operator fun plus(directive: GraphQLDirective): GraphQLTypes = copy(directives = directives + (directive.name to directive))

    operator fun plus(iface: GraphQLInterfaceType): GraphQLTypes = copy(interfaces = interfaces + (iface.name to iface))

    companion object {
        val empty: GraphQLTypes = GraphQLTypes(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
        )
    }
}

/** Generate arbitrary GraphQLTypes from a static Config */
fun Arb.Companion.graphQLTypes(cfg: Config = Config.default): Arb<GraphQLTypes> =
    Arb
        .graphQLNames(cfg)
        .flatMap { names ->
            graphQLTypes(names, cfg)
        }

/** Generate arbitrary GraphQLTypes from a static Config and static names */
fun Arb.Companion.graphQLTypes(
    names: GraphQLNames,
    cfg: Config = Config.default
): Arb<GraphQLTypes> =
    arbitrary { rs ->
        GraphQLTypesGen(cfg, names, rs).gen()
    }

private class InputTypeDescriptor(
    val underlyingTypeName: String,
    val underlyingTypeKind: TypeKind,
    val type: GraphQLInputType
) {
    // This is used to ensure that we do not create cyclic references that graphql-java will reject.
    // This matches GraphQL-Java validation rules, which will reject input
    // cycles that are technically satisfiable even when they contain non-nullable wrappers,
    // such as:
    // input Foo { foos: [[Foo]]! }
    val usedNonNullably: Boolean by lazy {
        tailrec fun loop(t: GraphQLType): Boolean =
            when (t) {
                is GraphQLNonNull -> true
                is GraphQLList -> loop(t.originalWrappedType)
                else -> false
            }
        loop(type)
    }
}

/** Internal helper class for generating a GraphQLTypes */
internal class GraphQLTypesGen(
    private val cfg: Config,
    private val names: GraphQLNames,
    private val rs: RandomSource
) {
    private fun sampleWeight(key: ConfigKey<Double>): Boolean = rs.sampleWeight(cfg[key])

    @JvmName("sampleCompoundingWeight")
    private fun sampleWeight(key: ConfigKey<CompoundingWeight>): Boolean = rs.sampleWeight(cfg[key].weight)

    fun gen(): GraphQLTypes =
        Arb
            .of(cfg[IncludeTypes])
            .withScalars()
            .withEnums()
            .withInputs()
            .withDirectives()
            .withInterfaces()
            .withObjects()
            .withUnions()
            .finalize()
            .next(rs)

    internal fun genDescription(): String = Arb.graphQLDescription(cfg).next(rs)

    private fun Arb<GraphQLTypes>.withDirectives(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(
                directives = types.directives + genDirectives().associateBy { it.name }
            )
        }

    private fun genDirectives(): List<GraphQLDirective> {
        val validLocations = DirectiveLocation
            .values()
            .toSet()
            .arbSubset(1..DirectiveLocation.values().size)

        return names.directives.map { name ->
            when (val d = builtinDirectives[name]) {
                null -> {
                    GraphQLDirective
                        .newDirective()
                        .name(name)
                        .description(genDescription())
                        .replaceArguments(genArguments(DirectiveHasArgs))
                        .repeatable(sampleWeight(DirectiveIsRepeatable))
                        .also {
                            validLocations.next(rs).forEach(it::validLocation)
                        }.build()
                }
                else -> d
            }
        }
    }

    private fun Arb<GraphQLTypes>.withScalars(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(scalars = types.scalars + genScalars().associateBy { it.name })
        }

    private fun genScalars(): List<GraphQLScalarType> =
        names.scalars.map { name ->
            when (val s = builtinScalars[name]) {
                null ->
                    GraphQLScalarType
                        .newScalar()
                        .name(name)
                        .description(genDescription())
                        .coercing(Scalars.GraphQLString.coercing)
                        .build()
                else -> s
            }
        }

    /**
     * Wrap a provided type in GraphQLList and GraphQLNonNull wrapper types.
     * This function may return a type that has been wrapped multiple times
     */
    internal fun decorate(
        t: GraphQLType,
        allowNonNullable: Boolean = true
    ): GraphQLType {
        tailrec fun wrap(
            type: GraphQLType,
            listBudget: Int
        ): GraphQLType =
            if (type !is GraphQLNonNull && allowNonNullable && sampleWeight(NonNullableTypeWeight)) {
                wrap(GraphQLNonNull.nonNull(type), listBudget)
            } else if (listBudget > 0 && sampleWeight(ListTypeWeight)) {
                wrap(GraphQLList.list(type), listBudget - 1)
            } else {
                type
            }

        return wrap(t, cfg[ListTypeWeight].max)
    }

    /**
     * Generate a GraphQLOutputType that is backed by a GraphQLTypeReference and potentially wrapped
     * in GraphQLNonNull and GraphQLList wrappers.
     */
    private fun genOutputTypeRef(): GraphQLOutputType =
        Arb
            .element(names.interfaces + names.scalars + names.unions + names.objects + names.enums)
            .map(GraphQLTypeReference::typeRef)
            .map {
                decorate(it) as GraphQLOutputType
            }.next(rs)

    /**
     * Generate a GraphQLInputType that is backed by a GraphQLTypeReference and potentially wrapped
     * in GraphQLNonNull and GraphQLList wrappers.
     */
    private fun genInputTypeDescriptor(allowNonNullable: Boolean = true): Arb<InputTypeDescriptor> {
        val namePools = setOf(TypeKind.Scalar, TypeKind.Input, TypeKind.Enum)
            .mapNotNull { tt ->
                names.names[tt]
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { pool -> tt to pool }
            }

        return Arb
            .element(namePools)
            .flatMap { (tt, pool) ->
                Arb.element(pool).map { tt to it }
            }.map { (tt, name) ->
                InputTypeDescriptor(
                    underlyingTypeName = name,
                    underlyingTypeKind = tt,
                    type = decorate(GraphQLTypeReference.typeRef(name), allowNonNullable) as GraphQLInputType
                )
            }
    }

    private fun Arb<GraphQLTypes>.withInterfaces(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(interfaces = types.interfaces + genInterfaces())
        }

    private fun genInterfaces(): Map<String, GraphQLInterfaceType> =
        names.interfaces.fold(emptyMap()) { acc, name ->
            val edges = genImplements(acc, InterfaceImplementsInterface)
            val localFields = genFields(InterfaceTypeSize)
            val allFields = (edges.flatMap { it.fields } + localFields)
                .distinctBy { it.name }

            val iface = GraphQLInterfaceType
                .newInterface()
                .name(name)
                .description(genDescription())
                .fields(allFields)
                .replaceInterfaces(edges.toList())
                .build()

            acc + (name to iface)
        }

    private fun genImplements(
        pool: Map<String, GraphQLInterfaceType>,
        weightKey: ConfigKey<CompoundingWeight>,
    ): Set<GraphQLInterfaceType> {
        tailrec fun withImplementedInterfaces(
            acc: Set<GraphQLInterfaceType>,
            unchecked: Set<GraphQLInterfaceType>
        ): Set<GraphQLInterfaceType> =
            if (unchecked.isEmpty()) {
                acc
            } else {
                val iface = unchecked.first()
                val parents = iface.interfaces.map { it as GraphQLInterfaceType }
                val toCheck = parents.filterNot(acc::contains).toSet()
                withImplementedInterfaces(
                    acc = acc + iface,
                    unchecked = (unchecked - iface + toCheck)
                )
            }

        tailrec fun loop(
            edges: Set<GraphQLInterfaceType>,
            edgeBudget: Int,
            pool: Map<String, GraphQLInterfaceType>
        ): Set<GraphQLInterfaceType> =
            if (edgeBudget > 0 && pool.isNotEmpty() && sampleWeight(weightKey)) {
                val newEdge = Arb.of(pool.entries).next(rs).value
                loop(
                    edges = edges + newEdge,
                    edgeBudget = edgeBudget - 1,
                    pool = pool - newEdge.name
                )
            } else {
                edges
            }

        // build base edges
        return loop(emptySet(), cfg[weightKey].max, pool)
            .let { edges ->
                // filter out conflicting edges
                edges.nonConflicting()
            }.let { edges ->
                // then add in all interfaces-of-interfaces
                withImplementedInterfaces(emptySet(), edges)
            }
    }

    private fun genArguments(key: ConfigKey<CompoundingWeight>): List<GraphQLArgument> {
        tailrec fun loop(acc: Map<String, GraphQLArgument>): List<GraphQLArgument> =
            if (acc.size != cfg[key].max && sampleWeight(key)) {
                val itd = genInputTypeDescriptor().next(rs)
                val arg = GraphQLArgument
                    .newArgument()
                    .name(Arb.graphQLArgumentName(cfg).next(rs))
                    .description(genDescription())
                    .type(itd.type)
                    .build()

                loop(acc = acc + (arg.name to arg))
            } else {
                acc.values.toList()
            }

        return loop(emptyMap())
    }

    private fun Arb<GraphQLTypes>.withObjects(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(objects = types.objects + genObjects(types).associateBy { it.name })
        }

    private fun genObjects(types: GraphQLTypes): List<GraphQLObjectType> =
        names.objects.map { name ->
            val implements = genImplements(
                types.interfaces,
                ObjectImplementsInterface
            )
            genObject(name, implements)
        }

    private fun genObject(
        name: String,
        implements: Set<GraphQLInterfaceType>,
    ): GraphQLObjectType {
        val interfaceFields = implements.flatMap { it.fields }
        val allFields = (interfaceFields + genFields(ObjectTypeSize))
            .distinctBy { it.name }

        return GraphQLObjectType
            .newObject()
            .name(name)
            .replaceInterfaces(implements.toList())
            .description(genDescription())
            .fields(allFields)
            .build()
    }

    private fun genFields(sizeKey: ConfigKey<IntRange>): List<GraphQLFieldDefinition> =
        Arb
            .int(cfg[sizeKey])
            .map { size ->
                List(size) { genField() }.distinctBy { it.name }
            }.next(rs)

    private fun genField(): GraphQLFieldDefinition =
        GraphQLFieldDefinition
            .newFieldDefinition()
            .name(Arb.graphQLFieldName(cfg).next(rs))
            .description(genDescription())
            .arguments(genArguments(FieldArgumentWeight))
            .type(genOutputTypeRef())
            .build()

    private fun Arb<GraphQLTypes>.withInputs(): Arb<GraphQLTypes> =
        map { types ->
            /**
             * Input objects have a unique requirement in that they must be constructed in a
             * way that allows for finite values. This is easiest to explain with counter-examples --
             * here are some type configurations that are invalid because they are uninhabited and
             * require infinite values:
             *
             * ```
             * // Recursive graphs with non-nullable edges:
             * input A { b:B! }
             * input B { a:A! }
             *
             * // Uninhabited OneOf types:
             * input A @oneOf { b:B }
             * input B @oneOf { a:A }
             *
             * // Mixed OneOf and non-nullable graphs:
             * input A @oneOf { b:B }
             * input B { a:A! }
             * ```
             *
             * The property that makes a type uninhabited is that the graph of its mandatory
             * fields (fields that *must* be provided a value) contains a cycle. If we construct types
             * such that any mandatory dependencies are a-cyclic, then we can guarantee inhabitation.
             *
             * The job of this method is to guarantee that every input object is inhabited, while
             * retaining the ability to generate as many interesting edge cases as possible.
             *
             * As a heuristic, we only allow input-typed mandatory fields where the name of the field's
             * type is lexicographically smaller than the name of the host's type. This ensures that
             * the graph of mandatory fields is acyclic. For example:
             *
             *   input A { b: B }    // field type "B" > host type "A"    ->  mandatory edge is not allowed
             *   input B { a: A! }   // field type "A" > host type "B"    ->  mandatory edge is allowed
             */
            val oneOfTypes = names.inputs.filter { sampleWeight(OneOfTypeWeight) }.toSet()

            val inputs = names.inputs.associateWith { name ->
                val isOneOf = name in oneOfTypes
                val fields = genInputFields(name, isOneOf, InputObjectTypeSize)

                GraphQLInputObjectType
                    .newInputObject()
                    .name(name)
                    .description(genDescription())
                    .fields(fields)
                    .apply {
                        if (isOneOf) {
                            withDirective(Directives.OneOfDirective)

                            // As a disjunctive type, if a OneOf contains no inhabited fields, then it
                            // is itself uninhabited.
                            // In these cases, we can make it inhabited by adding a single escape field
                            if (fields.none { isInhabitedField(name, it) }) {
                                field(genEscapeInputField())
                            }
                        }
                    }
                    .build()
            }
            types.copy(inputs = types.inputs + inputs)
        }

    private fun genInputFields(
        hostType: String,
        isOneOf: Boolean,
        key: ConfigKey<IntRange>
    ): List<GraphQLInputObjectField> {
        /**
         * To generate input fields, we start by generating an InputTypeDescriptor
         * and dropping the descriptors that don't meet the current nullability constraints.
         *
         * There are some configurations (like a small name pool or extreme configurations
         * for non-nullable types) that may make it impossible to meet the nullability
         * constraints.
         *
         * In these cases, generate up to 100x the number of requested fields
         * before falling back to an escape field.
         */
        val fieldCount = Arb.int(cfg[key]).next(rs)
        val fields = genInputTypeDescriptor(allowNonNullable = !isOneOf)
            .asSequence(rs)
            .take(fieldCount * 100)
            .filter { itd ->
                if (itd.usedNonNullably && itd.underlyingTypeKind == TypeKind.Input) {
                    itd.underlyingTypeName.compareTo(hostType) < 0
                } else {
                    true
                }
            }
            .take(fieldCount)
            .toList()
            .map { itd ->
                GraphQLInputObjectField
                    .newInputObjectField()
                    .name(Arb.graphQLFieldName(cfg).next(rs))
                    .description(genDescription())
                    .type(itd.type)
                    .build()
            }
            .distinctBy { it.name }

        return fields.ifEmpty { listOf(genEscapeInputField()) }
    }

    private fun genEscapeInputField(): GraphQLInputObjectField {
        val escapeFieldName = Arb.graphQLFieldName(cfg)
            // prefix the escape field name for better debuggability
            .map { "escape_" + it }
            .next(rs)

        return GraphQLInputObjectField.newInputObjectField()
            .name(escapeFieldName)
            .type(Scalars.GraphQLInt)
            .build()
    }

    /** An inhabited field is a field with a type that allows constructing a finite value. */
    private fun isInhabitedField(
        hostTypeName: String,
        field: GraphQLInputObjectField
    ): Boolean {
        // list-typed fields (even non-nullable lists) are inhabited because they can always
        // be constructed with an empty array ([])
        if (GraphQLTypeUtil.unwrapNonNull(field.type) is GraphQLList) return true

        val fieldTypeName = GraphQLTypeUtil.unwrapAllAs<GraphQLNamedType>(field.type).name
        // fields with simple types are always inhabited
        if (fieldTypeName in names.scalars || fieldTypeName in names.enums) return true

        /**
         * As a heuristic, fieldTypeName < hostTypeName is assumed to be inhabited.
         * See explanation in [withInputs].
         */
        return fieldTypeName.compareTo(hostTypeName) < 0
    }

    private fun Arb<GraphQLTypes>.withUnions(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(unions = types.unions + genUnions().associateBy { it.name })
        }

    private fun Arb<GraphQLTypes>.finalize(): Arb<GraphQLTypes> =
        if (cfg[GenInterfaceStubsIfNeeded]) {
            map(::addMissingImpls)
        } else {
            this
        }

    private fun addMissingImpls(types: GraphQLTypes): GraphQLTypes {
        tailrec fun loop(
            ifaces: Set<String>,
            objs: List<GraphQLObjectType>
        ): Set<String> =
            if (ifaces.isEmpty() || objs.isEmpty()) {
                ifaces
            } else {
                loop(
                    ifaces - objs
                        .first()
                        .interfaces
                        .map { it.name }
                        .toSet(),
                    objs.drop(1)
                )
            }

        val unimplemented = loop(types.interfaces.keys, types.objects.values.toList())
        val newObjects = unimplemented.map { iname ->
            val ifaces = types.interfaces.getValue(iname).let { iface ->
                val parents = iface.interfaces.map { types.interfaces.getValue(it.name) }
                parents.toSet() + iface
            }
            genObject(iname + "_STUB", ifaces)
        }
        return types.copy(
            objects = types.objects + newObjects.associateBy { it.name }
        )
    }

    private fun genUnions(): List<GraphQLUnionType> {
        if (names.unions.isEmpty() || names.objects.isEmpty()) {
            return emptyList()
        }

        // Arb.set can be finicky with what it calls slippage, which is when it wants to
        // generate a set of a certain size but has to make multiple attempts due to the
        // underlying generator returning the same element multiple times.
        // For unions, it's not critically important that we get the exact right size every
        // time, so just use a list generator and remove duplicates
        val arbMembers = Arb
            .list(
                gen = Arb.element(names.objects),
                range = IntRange(
                    min(names.objects.size, cfg[UnionTypeSize].first),
                    min(names.objects.size, cfg[UnionTypeSize].last)
                )
            ).map { it.distinct() }

        return names.unions.map { name ->
            val members = arbMembers.next(rs).map(::GraphQLTypeReference)

            GraphQLUnionType
                .newUnionType()
                .name(name)
                .description(genDescription())
                .replacePossibleTypes(members)
                .build()
        }
    }

    private fun genEnumValues(): List<GraphQLEnumValueDefinition> =
        // Generating enum values using Arb.set can throw an IllegalStateException if it can't
        // generate the target size. This becomes more likely as the target size grows and collisions
        // become harder to avoid.
        // Use Arb.list instead which is close enough and more reliable
        Arb
            .list(
                Arb.graphQLEnumValueName(cfg),
                cfg[EnumTypeSize]
            ).map { values ->
                values.toSet().map {
                    GraphQLEnumValueDefinition
                        .newEnumValueDefinition()
                        .name(it)
                        .value(it)
                        .build()
                }
            }.next(rs)

    private fun Arb<GraphQLTypes>.withEnums(): Arb<GraphQLTypes> =
        map { types ->
            types.copy(enums = types.enums + genEnums().associateBy { it.name })
        }

    private fun genEnums(): List<GraphQLEnumType> =
        names.enums.map { name ->
            GraphQLEnumType
                .newEnum()
                .name(name)
                .description(genDescription())
                .values(genEnumValues())
                .build()
        }
}
