package graphql.schema.property

import graphql.introspection.Introspection.DirectiveLocation
import graphql.introspection.Introspection.TypeNameMetaFieldDef
import graphql.language.Argument
import graphql.language.Directive
import graphql.language.Document
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.Node
import graphql.language.NullValue
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.Value
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.withEdgecases
import java.util.LinkedList
import kotlin.math.max

/**
 * Generate an arbitrary GraphQL [Document] for the provided [GraphQLSchema] and config.
 * The returned Document is guaranteed to be valid according to the rules at
 *   https://spec.graphql.org/draft/#sec-Documents
 */
fun Arb.Companion.graphQLDocument(
    schema: GraphQLSchema,
    cfg: Config = Config.default,
): Arb<Document> =
    arbitrary { rs ->
        DocumentGenEnv(schema, cfg, rs).documentGen.gen()
    }

/**
 * [DocumentGenEnv] provides an environment for component wiring, allowing for dependency cycles
 * This is declared as an interface to allow for kotlin-delegation inheritance
 */
internal interface DocumentGenEnv {
    val schemas: Schemas
    val cfg: Config
    val rs: RandomSource
    val documentGen: GraphQLDocumentGen
    val operationGen: GraphQLOperationGen
    val selectionSetGen: GraphQLSelectionSetGen
    val directiveGen: GraphQLDirectivesGen
    val argumentsGen: GraphQLArgumentsGen
    val inputValueGenerator: GraphQLInputValueGenerator

    fun checkDepth(depth: Int): Boolean = depth <= cfg[MaxSelectionSetDepth]

    companion object {
        operator fun invoke(
            schema: GraphQLSchema,
            cfg: Config,
            rs: RandomSource
        ): DocumentGenEnv = this(Schemas(schema), cfg, rs)

        operator fun invoke(
            schemas: Schemas,
            cfg: Config,
            rs: RandomSource
        ): DocumentGenEnv = DefaultDocumentGenEnv(schemas, cfg, rs)
    }
}

private data class DefaultDocumentGenEnv(
    override val schemas: Schemas,
    override val cfg: Config,
    override val rs: RandomSource
) : DocumentGenEnv {
    override val documentGen = GraphQLDocumentGen(this)
    override val operationGen = GraphQLOperationGen(this)
    override val selectionSetGen = GraphQLSelectionSetGen(this)
    override val directiveGen = GraphQLDirectivesGen(this)
    override val argumentsGen = GraphQLArgumentsGen(this)
    override val inputValueGenerator = GraphQLInputValueGenerator(
        schema = schemas.schema,
        config = cfg,
        randomSource = rs,
        uncoercedValueWeight = cfg[DocumentUncoercedValueWeight],
        allEdgesGraph = CycleGroups.allInputCycles(schemas.schema),
        mandatoryEdgesGraph = CycleGroups.mandatoryInputCycles(schemas.schema)
    )
}

internal data class DocumentGenCtx(
    val schemas: Schemas,
    val sb: SelectionsBuilder,
    val typeCondition: GraphQLCompositeType,
    val fragments: Fragments,
    val variables: Variables,
    val incrementalLabels: IncrementalLabels,
    val isSubscriptionOperation: Boolean,
    val depth: Int = 0,
) {
    val isSubscriptionSelection: Boolean get() = typeCondition == schemas.schema.subscriptionType
    val isMutationSelection: Boolean get() = typeCondition == schemas.schema.mutationType

    val typeAsImplementingType: GraphQLImplementingType get() = typeCondition as GraphQLImplementingType

    fun selectableFields(cfg: Config): List<GraphQLFieldDefinition> =
        when {
            typeCondition is GraphQLUnionType -> listOf(TypeNameMetaFieldDef)
            else ->
                typeAsImplementingType.fields
                    .let {
                        if (!isSubscriptionSelection) {
                            it + TypeNameMetaFieldDef
                        } else {
                            it
                        }
                    }
                    .filter { field ->
                        val coord = typeCondition.name to field.name
                        coord !in cfg[BanSelectionCoordinates]
                    }
        }

    val keepGenerating: Boolean get() =
        if (isSubscriptionSelection) sb.isEmpty else true

    /**
     * Create a new context with the provided [SelectionsBuilder] and [typeCondition], and
     * a depth counter incremented by 1
     */
    fun push(
        sb: SelectionsBuilder,
        typeCondition: GraphQLCompositeType
    ): DocumentGenCtx =
        copy(
            sb = sb,
            typeCondition = typeCondition,
            depth = depth + 1
        )

    companion object {
        operator fun invoke(
            schemas: Schemas,
            typeCondition: GraphQLCompositeType,
            isSubscriptionOperation: Boolean = false
        ): DocumentGenCtx =
            DocumentGenCtx(
                schemas,
                SelectionsBuilder(),
                typeCondition,
                Fragments(schemas),
                Variables(schemas),
                IncrementalLabels(),
                isSubscriptionOperation = isSubscriptionOperation,
                depth = 0
            )
    }
}

internal class GraphQLDocumentGen(
    env: DocumentGenEnv
) : DocumentGenEnv by env {
    fun gen(): Document {
        val db = DocumentBuilder(schemas)

        val arbOperation = Arb.of(
            listOfNotNull(
                schemas.schema.queryType to OperationDefinition.Operation.QUERY,
                schemas.schema.mutationType?.let { it to OperationDefinition.Operation.MUTATION },
                schemas.schema.subscriptionType?.let { it to OperationDefinition.Operation.SUBSCRIPTION }
            )
        )

        Arb
            .list(arbOperation, cfg[OperationCount])
            .next(rs)
            .also { list ->
                val count = list.size
                list.mapIndexed { i, (objectType, operationType) ->
                    val canBeAnonymous = i == 0 && count == 1
                    db.add(
                        operationGen.gen(db, objectType, operationType, canBeAnonymous)
                    )
                }
            }

        return db.build()
    }
}

internal class GraphQLOperationGen(
    env: DocumentGenEnv
) : DocumentGenEnv by env {
    /** generate a document that contains exactly 1 operation definition and any number of fragment definitions */
    fun gen(
        db: DocumentBuilder,
        objectType: GraphQLObjectType,
        operationType: OperationDefinition.Operation,
        canBeAnonymous: Boolean
    ): OperationDefinition {
        val variables = Variables(schemas)
        val name =
            if (canBeAnonymous && rs.sampleWeight(cfg[AnonymousOperationWeight])) {
                null
            } else {
                Arb
                    .graphQLName(cfg[TypeNameLength])
                    .filterNot { name -> db.operations.any { it.name == name } }
                    .next(rs)
            }

        val directiveLocation = when (operationType) {
            OperationDefinition.Operation.QUERY -> DirectiveLocation.QUERY
            OperationDefinition.Operation.MUTATION -> DirectiveLocation.MUTATION
            OperationDefinition.Operation.SUBSCRIPTION -> DirectiveLocation.SUBSCRIPTION
        }

        val ctx = DocumentGenCtx(schemas, SelectionsBuilder(), objectType, db.fragments, variables, db.incrementalLabels, isSubscriptionOperation = objectType == schemas.schema.subscriptionType)

        return OperationDefinition
            .newOperationDefinition()
            .name(name)
            .operation(operationType)
            .selectionSet(selectionSetGen.gen(ctx))
            .directives(directiveGen.gen(directiveLocation, ctx))
            .variableDefinitions(variables.variables)
            .build()
    }
}

internal class GraphQLSelectionSetGen(
    env: DocumentGenEnv
) : DocumentGenEnv by env {
    /** generate a SelectionSet for the given [DocumentGenCtx] */
    fun gen(ctx: DocumentGenCtx): SelectionSet {
        // check depth and return early
        if (checkDepth(ctx.depth)) {
            // fragments are more likely to be spreadable when applied to an empty selection set.
            // Add fragment spreads before generating other selections
            repeat(depthAdjustedCount(ctx, cfg[FragmentSpreadWeight])) {
                if (ctx.keepGenerating) {
                    genFragmentSpread(ctx)
                }
            }

            // add unfragmented field selections
            repeat(depthAdjustedCount(ctx, cfg[FieldSelectionWeight])) {
                if (ctx.keepGenerating) {
                    val field = Arb.of(ctx.selectableFields(cfg)).next(rs)
                    genField(ctx, field)
                }
            }

            // add inline fragments selections
            repeat(depthAdjustedCount(ctx, cfg[InlineFragmentWeight])) {
                if (ctx.keepGenerating) {
                    genInlineFragment(ctx)
                }
            }
        }

        // if no selections were added, add a default selection to ensure that the selection set is syntactically valid
        if (ctx.sb.isEmpty) {
            val fallbackField = Arb.of(fallbackFields(ctx)).next(rs)
            genField(ctx, fallbackField)
        }

        return ctx.sb.build()
    }

    private fun depthAdjustedCount(
        ctx: DocumentGenCtx,
        cw: CompoundingWeight
    ): Int {
        val adjustedWeight = cw.copy(max = max(cw.max - ctx.depth, 0))
        return rs.count(adjustedWeight)
    }

    private fun fallbackFields(ctx: DocumentGenCtx): List<GraphQLFieldDefinition> =
        ctx.selectableFields(cfg)
            .filter { GraphQLTypeUtil.unwrapAll(it.type) !is GraphQLCompositeType }

    private fun genField(
        ctx: DocumentGenCtx,
        field: GraphQLFieldDefinition
    ) {
        val arguments = argumentsGen.gen(field.arguments, ctx)
        val unwrappedFieldType = GraphQLTypeUtil.unwrapAllAs<GraphQLOutputType>(field.type)
        var key = FieldKey(
            fieldName = field.name,
            alias = null,
            arguments = arguments.map(::ArgumentKey).toSet(),
            fieldType = TypeExpr(field.type)
        )

        val fieldCompositeType = unwrappedFieldType as? GraphQLCompositeType

        if (!ctx.sb.canAdd(key) || rs.sampleWeight(cfg[AliasWeight])) {
            key = withAlias(ctx, key, cfg[FieldNameLength])
        }

        val directives = genDirectives(ctx, DirectiveLocation.FIELD)

        val fieldSelections = fieldCompositeType?.let { ctx.sb.newFieldScope(key) }
        if (fieldSelections != null) {
            gen(ctx.push(fieldSelections, fieldCompositeType))
        }

        val fieldSelection = FieldSelection(key, fieldSelections, directives)
        ctx.sb.add(fieldSelection)
    }

    private fun withAlias(
        ctx: DocumentGenCtx,
        key: FieldKey,
        aliasNameSize: IntRange
    ): FieldKey {
        // when generating an alias name, generate aliases that can collide with
        // field names. These values are read using Arb.generate (rather than Arb.next),
        // which is one of a small number of Arb methods that do not ignore edge cases
        val keyWithAlias = Arb
            .graphQLName(aliasNameSize)
            .withEdgecases(ctx.selectableFields(cfg).map { it.name })
            .generate(rs)
            .take(100)
            .map { key.copy(alias = it.value) }
            .filter(ctx.sb::canAdd)
            .firstOrNull()

        if (keyWithAlias !== null) return keyWithAlias

        // For low values of aliasNameSize, it's possible that all possible alias names
        // are already in use and that we cannot generate a non-conflicting alias name.
        // Try to generate an alias with the requested length, but expand
        // the length if we don't find a value in the first couple iterations
        val newAliasNameSize = (aliasNameSize.first + 1)..(aliasNameSize.last + 1)
        return withAlias(ctx, key, newAliasNameSize)
    }

    private fun genInlineFragment(ctx: DocumentGenCtx) {
        val spreadableTypes = schemas.rels.spreadableTypes(ctx.typeCondition)
        if (spreadableTypes.isEmpty()) return

        val typeCondition = Arb.of(spreadableTypes).next(rs)
        val spreadScope = ctx.sb.newSpreadScope()
        val spreadCtx = ctx.push(spreadScope, typeCondition)
        gen(spreadCtx)

        val isUntyped = typeCondition == ctx.typeCondition && rs.sampleWeight(cfg[UntypedInlineFragmentWeight])
        val sdlTypeCondition = if (isUntyped) null else typeCondition.name

        val directives = directiveGen.gen(DirectiveLocation.INLINE_FRAGMENT, ctx)

        val selection = InlineFragmentSelection(
            sdlTypeCondition,
            spreadCtx.sb.selections,
            directives
        )
        ctx.sb.add(selection)
    }

    private fun genFragmentSpread(ctx: DocumentGenCtx) {
        val spreadableFragments = ctx.fragments
            .spreadableFragments(ctx.sb, ctx.typeCondition)
            .filter { frag ->
                // a fragment may have been generated in the context of another operation, and used variables
                // that are incompatible with the variables defined for this operation. Filter them out
                frag.variables.all { fv ->
                    when (val extant = ctx.variables[requireNotNull(fv.name)]) {
                        null -> true
                        else -> extant == fv
                    }
                }
            }

        var fragment: FragmentDef? = null

        if (spreadableFragments.isEmpty() || rs.sampleWeight(cfg[FragmentDefinitionWeight])) {
            val spreadableTypes = schemas.rels.spreadableTypes(ctx.typeCondition)
            if (spreadableTypes.isNotEmpty()) {
                val fragmentTypeCondition = Arb.of(spreadableTypes).next(rs)
                fragment = genFragmentDef(ctx, fragmentTypeCondition)
            }
        }

        if (fragment == null && spreadableFragments.isNotEmpty()) {
            fragment = Arb.of(spreadableFragments).next(rs)
        }

        if (fragment == null) return

        // if the fragment was generated for a different operation, we may need to install any new variables
        // required by the fragment into the current operation's variables
        fragment.variables.forEach { fv ->
            if (ctx.variables[requireNotNull(fv.name)] == null) {
                ctx.variables.add(fv)
            }
        }

        val selection = FragmentSpreadSelection(
            fragment.name,
            fragment.sb.selections,
            genDirectives(ctx, DirectiveLocation.FRAGMENT_SPREAD)
        )

        ctx.sb.add(selection)
    }

    /** generate a fragment definition that is spreadable in the selections of Ctx */
    private fun genFragmentDef(
        ctx: DocumentGenCtx,
        typeCondition: GraphQLCompositeType
    ): FragmentDef {
        val fragmentScope = ctx.sb.newFragmentScope()

        val fragCtx = ctx.push(fragmentScope, typeCondition).also(::gen)

        val name = Arb
            .graphQLName(cfg[TypeNameLength])
            // prefix fragment names to help with readability when debugging
            .map { "Fragment_$it" }
            .filter { ctx.fragments[it] == null }
            .next(rs)

        val directives = genDirectives(fragCtx, DirectiveLocation.FRAGMENT_DEFINITION)
        val def = FragmentDefinition
            .newFragmentDefinition()
            .name(name)
            .typeCondition(TypeName(typeCondition.name))
            .directives(directives)
            .selectionSet(fragCtx.sb.build())
            .build()

        val variables = extractVariables(ctx, def)
        return FragmentDef(name, typeCondition, def, fragCtx.sb, variables)
            .also(fragCtx.fragments::add)
    }

    /**
     * Extract any variable definitions that are used in a nodes tree, inclusive
     * of any referenced fragments or variables (which can also refer to other variables)
     */
    private fun extractVariables(
        ctx: DocumentGenCtx,
        node: Node<*>
    ): List<VariableDefinition> {
        val seen = mutableSetOf<Node<*>>()
        val vars = mutableSetOf<VariableDefinition>()
        val queue = LinkedList(listOf(node))

        while (queue.isNotEmpty()) {
            val headNode = queue.remove().also(seen::add)

            headNode.allChildren.forEach { child ->
                if (child is FragmentSpread && child !in seen) {
                    seen.add(child)
                    queue.push(requireNotNull(ctx.fragments[requireNotNull(child.name)]).def)
                }
                if (child is VariableReference && child !in seen) {
                    val vdef = requireNotNull(ctx.variables[requireNotNull(child.name)])
                    seen.add(child)
                    vars.add(vdef)
                }
            }
        }
        return vars.toList()
    }

    private fun genDirectives(
        ctx: DocumentGenCtx,
        location: DirectiveLocation
    ): List<Directive> = directiveGen.gen(location, ctx)
}

internal class GraphQLArgumentsGen(
    env: DocumentGenEnv
) : DocumentGenEnv by env {
    fun gen(
        args: List<GraphQLArgument>,
        ctx: DocumentGenCtx,
        canUseVariables: Boolean = true
    ): List<Argument> = args.mapNotNull { gen(it, ctx, canUseVariables) }

    /**
     * generate a single Argument value for the provided GraphQLArgument.
     * A null value indicates that the Argument should be omitted
     */
    fun gen(
        arg: GraphQLArgument,
        ctx: DocumentGenCtx,
        canUseVariables: Boolean = true
    ): Argument? {
        val canInull = arg.hasSetDefaultValue() || GraphQLTypeUtil.isNullable(arg.type)

        return if (canInull && rs.sampleWeight(cfg[ImplicitNullValueWeight])) {
            null
        } else {
            val argDefault = arg.argumentDefaultValue
                .takeIf { it.isLiteral }
                ?.let {
                    it.value as Value<*>
                }

            Argument(arg.name, genValue(arg.type, ctx, argDefault, canUseVariables))
        }
    }

    private fun genValue(
        type: GraphQLInputType,
        ctx: DocumentGenCtx,
        locationDefault: Value<*>?,
        canUseVariables: Boolean
    ): Value<*> {
        // TODO: deeply insert variable values into values (ie inside list items, or input fields)
        return if (canUseVariables && rs.sampleWeight(cfg[VariableWeight])) {
            val possibleVariables = ctx.variables.variablesForType(type, locationDefault)
            val variable = if (possibleVariables.isEmpty()) {
                genVariable(type, ctx)
            } else {
                Arb.of(possibleVariables).next(rs)
            }
            VariableReference(requireNotNull(variable.name))
        } else {
            inputValueGenerator.generate(type)
        }
    }

    private fun genVariable(
        forType: GraphQLInputType,
        ctx: DocumentGenCtx,
    ): VariableDefinition {
        val default =
            if (rs.sampleWeight(cfg[DefaultValueWeight])) {
                inputValueGenerator.generate(forType)
            } else {
                null
            }

        val name = Arb
            .graphQLFieldName(cfg)
            .filter { ctx.variables[it] == null }
            .next(rs)

        return VariableDefinition
            .newVariableDefinition()
            .name(name)
            .type(forType.asAstType())
            .defaultValue(default)
            .directives(
                // A nuance of the spec grammar that isn't spelled out in the prose is that
                // directives applied to variable definitions may not themselves use variables
                directiveGen.gen(DirectiveLocation.VARIABLE_DEFINITION, ctx, canUseVariables = false)
            ).build()
            .also(ctx.variables::add)
    }
}

internal class GraphQLDirectivesGen(
    env: DocumentGenEnv
) : DocumentGenEnv by env {
    // a set of directives that are known to require const argument values
    private val incrementalDirs = setOf("stream", "defer")
    private val conditionalDirs = setOf("skip", "include")

    fun gen(
        location: DirectiveLocation,
        ctx: DocumentGenCtx,
        canUseVariables: Boolean = true,
    ): List<Directive> {
        val directiveWeight = cfg[AppliedDirectiveWeight]

        tailrec fun loop(
            acc: List<Directive>,
            pool: Set<GraphQLDirective>
        ): List<Directive> =
            if (pool.isEmpty() || directiveWeight.max == acc.size || !rs.sampleWeight(directiveWeight.weight)) {
                acc
            } else {
                val def = Arb.of(pool).next(rs)
                val useVariables = canUseVariables && def.name !in incrementalDirs
                val newPool = if (def.isRepeatable) pool else pool - def
                val arguments = genArguments(def.arguments, ctx, useVariables, isIncremental = def.name in incrementalDirs)
                val dir = Directive
                    .newDirective()
                    .name(def.name)
                    .arguments(arguments)
                    .build()
                loop(acc + dir, newPool)
            }
        val pool = schemas.directivesByLocation[location]?.let { pool ->
            val bannedDirectives = buildSet {
                addAll(cfg[BanDirectiveNames])

                if (ctx.isSubscriptionOperation || ctx.isMutationSelection) {
                    // incremental directives are not allowed anywhere in a subscription operation or on a
                    // mutation root field
                    addAll(incrementalDirs)
                }
                if (ctx.isSubscriptionSelection) {
                    // subscriptions have a single-root-field rule that makes it difficult to sanely apply skip/include
                    // directives. If we're evaluating directives for a subscription selection, remove these directives
                    // from the pool
                    // see also: https://spec.graphql.org/draft/#sec-Single-Root-Field
                    addAll(conditionalDirs)
                }
            }
            if (bannedDirectives.isNotEmpty()) {
                pool.filter { it.name !in bannedDirectives }
            } else {
                pool
            }
        } ?: emptySet()
        return loop(emptyList(), pool.toSet())
    }

    private fun genArguments(
        defs: List<GraphQLArgument>,
        ctx: DocumentGenCtx,
        canUseVariables: Boolean,
        isIncremental: Boolean
    ): List<Argument> {
        // check that the argument is valid. Invalid arguments may be regenerated
        fun checkArgument(
            def: GraphQLArgument,
            arg: Argument?
        ): Boolean {
            if (!isIncremental || def.name != "label") return true

            val label = when (val v = arg?.value) {
                is StringValue -> v.value
                null, is NullValue -> null
                else -> throw IllegalStateException("Unexpected value type: $v")
            }

            return when {
                label == null -> true
                ctx.incrementalLabels.contains(label) -> false
                else -> {
                    ctx.incrementalLabels.add(label)
                    true
                }
            }
        }

        // iteratively generate Argument values for the given definitions
        tailrec fun loop(
            acc: List<Argument>,
            pending: List<GraphQLArgument>
        ): List<Argument> {
            if (pending.isEmpty()) return acc
            val def = pending[0]
            val arg = argumentsGen.gen(def, ctx, canUseVariables)
            return if (checkArgument(def, arg)) {
                val newAcc = arg?.let { acc + it } ?: acc
                loop(newAcc, pending.drop(1))
            } else {
                // if checkArgument rejected the generated arg, do not dequeue and keep looping until we get an acceptable value
                loop(acc, pending)
            }
        }

        return loop(emptyList(), defs)
    }
}
