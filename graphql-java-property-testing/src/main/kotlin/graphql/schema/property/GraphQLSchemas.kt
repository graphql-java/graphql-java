package graphql.schema.property

import graphql.Directives
import graphql.Scalars
import graphql.introspection.Introspection.DirectiveLocation
import graphql.language.Value
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCodeRegistry
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
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.GraphQLUnionType
import graphql.schema.GraphQLUnmodifiedType
import graphql.schema.SchemaTransformer
import graphql.schema.TypeResolver
import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of

/** Generate arbitrary [GraphQLSchema]s from a static [Config] */
fun Arb.Companion.graphQLSchema(cfg: Config = Config.default): Arb<GraphQLSchema> =
    Arb.graphQLTypes(cfg).flatMap { types ->
        graphQLSchema(types, cfg)
    }

/** Generate arbitrary [GraphQLSchema]s from a [GraphQLTypes] */
@JvmName("arbGraphQLSchema")
fun Arb.Companion.graphQLSchema(
    types: GraphQLTypes,
    cfg: Config = Config.default
): Arb<GraphQLSchema> =
    arbitrary { rs ->
        SchemaGenerator(cfg, rs).createSchema(types)
    }

/** Methods for generating GraphQLSchema instances */
internal class SchemaGenerator(val cfg: Config, val rs: RandomSource) {
    private val emptyQuery: GraphQLObjectType =
        GraphQLObjectType
            .newObject()
            .name("EmptyQuery")
            .field(
                GraphQLFieldDefinition
                    .newFieldDefinition()
                    .name("placeholder")
                    .type(Scalars.GraphQLInt)
                    .build()
            ).build()

    private fun codeRegistry(types: GraphQLTypes): GraphQLCodeRegistry {
        val typeResolver = TypeResolver {
            throw UnsupportedOperationException("Generated schemas do not provide runtime type resolution")
        }

        return GraphQLCodeRegistry
            .newCodeRegistry()
            .also {
                types.interfaces.forEach { (name, _) ->
                    it.typeResolver(name, typeResolver)
                }
                types.unions.forEach { (name, _) ->
                    it.typeResolver(name, typeResolver)
                }
            }.build()
    }

    /** Generate a GraphQLSchema from a GraphQLTypes */
    fun createSchema(types: GraphQLTypes): GraphQLSchema {
        val queryType = types.objects.entries.firstOrNull()?.value ?: emptyQuery
        return GraphQLSchema
            .newSchema()
            .query(queryType)
            .additionalTypes(types.interfaces.values.toSet())
            .additionalTypes((types.objects.values.toSet() - queryType))
            .additionalTypes(types.inputs.values.toSet())
            .additionalTypes(types.unions.values.toSet())
            .additionalTypes(types.scalars.values.toSet())
            .additionalTypes(types.enums.values.toSet())
            .additionalDirectives(types.directives.values)
            .codeRegistry(codeRegistry(types))
            .build()
            .let(::finalize)
    }

    fun createSchema(sdl: String): GraphQLSchema {
        val tdr = SchemaParser().parse(sdl)
        return FastSchemaGenerator()
            .makeExecutableSchema(tdr, RuntimeWiring.MOCKED_WIRING)
    }

    fun finalize(schema: GraphQLSchema): GraphQLSchema {
        val withDefaults = addDefaults(schema)
        val valueGenerator = inputValueGenerator(withDefaults)
        return SchemaTransformer.transformSchema(
            withDefaults,
            AddAppliedDirectives(withDefaults, valueGenerator, cfg, rs)
        )
    }

    private fun addDefaults(schema: GraphQLSchema): GraphQLSchema {
        if (cfg[DefaultValueWeight] == 0.0) return schema
        val valueGenerator = inputValueGenerator(schema)
        return SchemaTransformer.transformSchema(schema, AddDefaults(valueGenerator, cfg, rs))
    }

    private fun inputValueGenerator(schema: GraphQLSchema): GraphQLInputValueGenerator =
        GraphQLInputValueGenerator(
            schema = schema,
            config = cfg,
            randomSource = rs,
            uncoercedValueWeight = cfg[SchemaUncoercedValueWeight],
            allEdgesGraph = CycleGroups.allInputCycles(schema),
            mandatoryEdgesGraph = CycleGroups.mandatoryInputCycles(schema)
        )
}

/** Adds generated literal defaults to arguments and non-oneOf input fields. */
internal class AddDefaults(
    private val inputValueGenerator: GraphQLInputValueGenerator,
    private val cfg: Config,
    private val rs: RandomSource
) : GraphQLTypeVisitorStub() {
    // don't traverse into built-in directives
    override fun visitGraphQLDirective(
        node: GraphQLDirective,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        if (node.name in builtinDirectives) {
            TraversalControl.ABORT
        } else {
            TraversalControl.CONTINUE
        }

    override fun visitGraphQLInputObjectField(
        field: GraphQLInputObjectField,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl {
        // don't add default values for one of fields
        val parent = context.parentNode
        if (parent is GraphQLInputObjectType && parent.isOneOf) {
            return TraversalControl.CONTINUE
        }

        if (rs.sampleWeight(cfg[DefaultValueWeight])) {
            changeNode(
                context,
                field.transform {
                    it.defaultValueLiteral(genDefaultValue(field.type))
                }
            )
        }

        return TraversalControl.CONTINUE
    }

    override fun visitGraphQLArgument(
        arg: GraphQLArgument,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl {
        if (rs.sampleWeight(cfg[DefaultValueWeight])) {
            changeNode(
                context,
                arg.transform {
                    it.defaultValueLiteral(genDefaultValue(arg.type))
                }
            )
        }

        return TraversalControl.CONTINUE
    }

    private fun genDefaultValue(type: GraphQLInputType): Value<*> {
        return inputValueGenerator.generate(type)
    }
}

private data class DirectiveDependencyInfo(
    val directives: Set<String>,
    val referencedInputLikeTypes: Set<String>
)

/** Adds schema-valid directive applications while avoiding directive dependency cycles. */
internal class AddAppliedDirectives(
    private val schema: GraphQLSchema,
    private val inputValueGenerator: GraphQLInputValueGenerator,
    private val cfg: Config,
    private val rs: RandomSource
) : GraphQLTypeVisitorStub() {
    private val directivesByLocation =
        schema.directives.fold(emptyMap<DirectiveLocation, Set<GraphQLDirective>>()) { acc, dir ->
            if (dir.name in cfg[BanDirectiveNames]) {
                acc
            } else if (dir.name == Directives.OneOfDirective.name) {
                // @oneOf is applied during type generation, we can skip applying it here
                acc
            } else {
                dir.validLocations().fold(acc) { innerAcc, loc ->
                    val newDirs = (innerAcc[loc] ?: emptySet()) + dir
                    innerAcc + (loc to newDirs)
                }
            }
        }

    private val directivesByName =
        schema.directives
            .filter { it.name !in builtinDirectives && it.name !in cfg[BanDirectiveNames] }
            .associateBy { it.name }

    private val directiveDependencyInfoByName =
        directivesByName.mapValues { (_, directive) ->
            buildDirectiveDependencyInfo(directive)
        }

    private val directivesByReferencedInputLikeType: Map<String, Set<String>> =
        directiveDependencyInfoByName.entries
            .fold(mutableMapOf<String, MutableSet<String>>()) { acc, (directiveName, info) ->
                info.referencedInputLikeTypes.forEach { typeName ->
                    acc.getOrPut(typeName) { mutableSetOf() }.add(directiveName)
                }
                acc
            }

    private val directiveDependencies: MutableMap<String, MutableSet<String>> =
        directiveDependencyInfoByName.mapValuesTo(mutableMapOf()) { (_, info) ->
            info.directives.toMutableSet()
        }

    // don't traverse into built-in directives
    override fun visitGraphQLDirective(
        node: GraphQLDirective,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        if (node.name in builtinDirectives) {
            TraversalControl.ABORT
        } else {
            TraversalControl.CONTINUE
        }

    override fun visitGraphQLArgument(
        node: GraphQLArgument,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(
            context,
            DirectiveLocation.ARGUMENT_DEFINITION,
            // non-nullable arguments may not be deprecated
            allowDeprecated = node.type !is GraphQLNonNull
        ) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    override fun visitGraphQLObjectType(
        node: GraphQLObjectType,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(context, DirectiveLocation.OBJECT) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    override fun visitGraphQLFieldDefinition(
        node: GraphQLFieldDefinition,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(context, DirectiveLocation.FIELD_DEFINITION) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    override fun visitGraphQLInterfaceType(
        node: GraphQLInterfaceType,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(context, DirectiveLocation.INTERFACE) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    override fun visitGraphQLUnionType(
        node: GraphQLUnionType,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(context, DirectiveLocation.UNION) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    override fun visitGraphQLEnumType(
        node: GraphQLEnumType,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(context, DirectiveLocation.ENUM) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    override fun visitGraphQLEnumValueDefinition(
        node: GraphQLEnumValueDefinition,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(context, DirectiveLocation.ENUM_VALUE) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    override fun visitGraphQLInputObjectType(
        node: GraphQLInputObjectType,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(context, DirectiveLocation.INPUT_OBJECT) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    override fun visitGraphQLInputObjectField(
        node: GraphQLInputObjectField,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl =
        replaceAppliedDirectives(
            context,
            DirectiveLocation.INPUT_FIELD_DEFINITION,
            // non-nullable input fields may not be deprecated
            allowDeprecated = node.type !is GraphQLNonNull
        ) { dirs ->
            node.transform {
                it.replaceAppliedDirectives(dirs)
            }
        }

    private fun replaceAppliedDirectives(
        ctx: TraverserContext<GraphQLSchemaElement>,
        loc: DirectiveLocation,
        allowDeprecated: Boolean = true,
        doReplace: (dirs: List<GraphQLAppliedDirective>) -> GraphQLSchemaElement
    ): TraversalControl =
        ctx.unlessIntrospection {
            val ownerDirectives = ctx.ownerDirectiveNames()
            val dirs = genAppliedDirectives(
                loc = loc,
                traversedDirectives = ctx.collectTraversedDirectives(),
                allowDeprecated = allowDeprecated,
                ownerDirectives = ownerDirectives
            )
            if (dirs.isEmpty()) {
                TraversalControl.CONTINUE
            } else {
                if (ownerDirectives.isNotEmpty()) {
                    val newDependencies = dirs.map { it.name }
                    ownerDirectives.forEach { ownerDirective ->
                        directiveDependencies
                            .getOrPut(ownerDirective) { mutableSetOf() }
                            .addAll(newDependencies)
                    }
                }
                changeNode(ctx, doReplace(dirs))
            }
        }

    private fun genAppliedDirectives(
        loc: DirectiveLocation,
        traversedDirectives: Set<String>,
        allowDeprecated: Boolean,
        ownerDirectives: Set<String>
    ): List<GraphQLAppliedDirective> {
        tailrec fun loop(
            acc: List<GraphQLAppliedDirective>,
            pool: Set<GraphQLDirective>
        ): List<GraphQLAppliedDirective> =
            if (pool.isNotEmpty() && acc.size != cfg[AppliedDirectiveWeight].max && rs.sampleWeight(cfg[AppliedDirectiveWeight].weight)) {
                val dir = Arb.of(pool).next(rs)
                val applied = dir
                    .toAppliedDirective()
                    .transform {
                        val args = dir.arguments.map { arg ->
                            arg.toAppliedArgument().transform { b ->
                                b.valueLiteral(inputValueGenerator.generate(arg.type))
                                // to placate graphql-java's type-consistency checker, set the type to a GraphQLTypeReference
                                b.type(arg.type.asReference)
                            }
                        }
                        it.replaceArguments(args)
                    }
                loop(
                    acc = acc + applied,
                    pool = if (dir.isRepeatable) pool else (pool - dir)
                )
            } else {
                acc
            }

        val pool = candidateDirectives(loc, traversedDirectives, allowDeprecated, ownerDirectives)
        return loop(emptyList(), pool.toSet())
    }

    private fun candidateDirectives(
        loc: DirectiveLocation,
        traversedDirectives: Set<String>,
        allowDeprecated: Boolean,
        ownerDirectives: Set<String>
    ): Set<GraphQLDirective> =
        (directivesByLocation[loc] ?: emptySet())
            .let { pool ->
                // some locations don't allow application of @deprecated.
                // Remove it from the pool if necessary
                if (!allowDeprecated) {
                    pool.filterNot { it.name == Directives.DeprecatedDirective.name }.toSet()
                } else {
                    pool
                }
            }
            .let { pool ->
                // In order to prevent cycles of directive application, remove any directives that
                // have already been applied in our traversal path.
                if (traversedDirectives.isNotEmpty()) {
                    pool.filter { it.name !in traversedDirectives }.toSet()
                } else {
                    pool
                }
            }.let { pool ->
                // Some input and enum nodes are shared across multiple directive definitions.
                // Remove any directive that would make any owning directive depend on itself.
                // This keeps the generated directive dependency graph acyclic.
                if (ownerDirectives.isNotEmpty()) {
                    pool.filterNot { candidate ->
                        ownerDirectives.any { ownerDirective ->
                            wouldCreateDirectiveCycle(ownerDirective, candidate.name)
                        }
                    }.toSet()
                } else {
                    pool
                }
            }

    private fun wouldCreateDirectiveCycle(
        hostDirective: String,
        candidateDirective: String
    ): Boolean {
        if (hostDirective == candidateDirective) {
            return true
        }

        val seen = mutableSetOf<String>()

        fun canReach(current: String): Boolean {
            if (!seen.add(current)) {
                return false
            }
            if (current == hostDirective) {
                return true
            }
            return directiveDependencies[current]?.any(::canReach) == true
        }

        return canReach(candidateDirective)
    }

    private fun buildDirectiveDependencyInfo(directive: GraphQLDirective): DirectiveDependencyInfo {
        val dependencies = mutableSetOf<String>()
        val traversedDirectiveDefs = mutableSetOf<String>()
        val referencedInputLikeTypes = mutableSetOf<String>()
        val traversedInputLikeTypes = mutableSetOf<String>()
        val traverser =
            Traverser.depthFirstWithNamedChildren<GraphQLSchemaElement>(
                { element ->
                    dependencyChildren(
                        element = element,
                        dependencies = dependencies,
                        referencedInputLikeTypes = referencedInputLikeTypes,
                        traversedDirectiveDefs = traversedDirectiveDefs,
                        traversedInputLikeTypes = traversedInputLikeTypes
                    )
                },
                null,
                null
            )
        val visitor = object : TraverserVisitorStub<GraphQLSchemaElement>() {}

        directive.arguments.forEach { arg ->
            traverser.traverse(arg, visitor)
        }

        return DirectiveDependencyInfo(
            directives = dependencies,
            referencedInputLikeTypes = referencedInputLikeTypes
        )
    }

    private fun dependencyChildren(
        element: GraphQLSchemaElement,
        dependencies: MutableSet<String>,
        referencedInputLikeTypes: MutableSet<String>,
        traversedDirectiveDefs: MutableSet<String>,
        traversedInputLikeTypes: MutableSet<String>
    ): Map<String, List<GraphQLSchemaElement>> =
        when (element) {
            is GraphQLAppliedDirective -> {
                dependencies.add(element.name)
                val directiveDef = directivesByName[element.name]
                if (directiveDef != null && traversedDirectiveDefs.add(directiveDef.name)) {
                    mapOf("directiveDefinition" to listOf(directiveDef))
                } else {
                    emptyMap()
                }
            }
            is GraphQLInputObjectType, is GraphQLEnumType -> {
                val namedType = element as GraphQLNamedType
                referencedInputLikeTypes.add(namedType.name)
                if (traversedInputLikeTypes.add(namedType.name)) {
                    resolvedChildren(element)
                } else {
                    emptyMap()
                }
            }
            else -> resolvedChildren(element)
        }

    private fun resolvedChildren(element: GraphQLSchemaElement): Map<String, List<GraphQLSchemaElement>> =
        element.childrenWithTypeReferences.children.mapValues { (_, children) ->
            children.mapNotNull { child ->
                when (child) {
                    is GraphQLTypeReference -> schema.getType(child.name)
                    is GraphQLSchemaElement -> child
                    else -> null
                }
            }
        }

    private val GraphQLInputType.asReference: GraphQLInputType
        get() =
            when (this) {
                is GraphQLList ->
                    GraphQLList((this.wrappedType as GraphQLInputType).asReference)
                is GraphQLNonNull ->
                    GraphQLNonNull((this.wrappedType as GraphQLInputType).asReference)
                is GraphQLTypeReference -> this
                is GraphQLUnmodifiedType -> GraphQLTypeReference(name)
                else -> throw IllegalArgumentException("Unsupported type: $this")
            }

    private fun TraverserContext<GraphQLSchemaElement>.unlessIntrospection(fn: () -> TraversalControl): TraversalControl {
        val node = thisNode()
        if (node is GraphQLNamedType && node.name.startsWith("__")) {
            return TraversalControl.ABORT
        }
        if (node is GraphQLFieldDefinition && node.name.startsWith("__")) {
            return TraversalControl.ABORT
        }
        return fn()
    }

    private fun TraverserContext<GraphQLSchemaElement>.collectTraversedDirectives(): Set<String> = parentNodes.mapNotNull { (it as? GraphQLDirective)?.name }.toSet()

    private fun TraverserContext<GraphQLSchemaElement>.containingDirectiveName(): String? = parentNodes.firstNotNullOfOrNull { (it as? GraphQLDirective)?.name }

    private fun TraverserContext<GraphQLSchemaElement>.ownerDirectiveNames(): Set<String> =
        mutableSetOf<String>().also { names ->
            containingDirectiveName()?.let(names::add)
            collectContainingInputLikeTypeNames().forEach { typeName ->
                names.addAll(directivesByReferencedInputLikeType[typeName].orEmpty())
            }
        }

    private fun TraverserContext<GraphQLSchemaElement>.collectContainingInputLikeTypeNames(): Set<String> =
        mutableSetOf<String>().also { names ->
            parentNodes.forEach { element ->
                element.inputLikeTypeName()?.let(names::add)
            }
            thisNode().inputLikeTypeName()?.let(names::add)
        }

    private fun GraphQLSchemaElement.inputLikeTypeName(): String? =
        when (this) {
            is GraphQLInputObjectType -> name
            is GraphQLEnumType -> name
            else -> null
        }
}
