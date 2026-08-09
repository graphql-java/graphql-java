package graphql.arbitrary

import graphql.language.Document
import graphql.language.ListType
import graphql.language.Node
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import graphql.parser.Parser
import graphql.parser.ParserEnvironment
import graphql.parser.ParserOptions
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnmodifiedType
import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.util.TraversalControl
import graphql.util.TraverserContext

/** Return a mocked [GraphQLSchema] described by this String value. */
val String.asSchema: GraphQLSchema
    get() = FastSchemaGenerator().makeExecutableSchema(SchemaParser().parse(this), RuntimeWiring.MOCKED_WIRING)

/** Return a parsed [Document] described by this String value. */
val String.asDocument: Document
    get() = Parser.parse(
        ParserEnvironment.newParserEnvironment()
            .document(this)
            .parserOptions(ParserOptions.getDefaultSdlParserOptions())
            .build()
    )

/** All descendants of this AST node, excluding the node itself. */
internal val Node<*>.allChildren: List<Node<*>>
    get() = children.fold(children.toList()) { descendants, child ->
        descendants + child.allChildren
    }

/** All descendants of this AST node that have type [T]. */
internal inline fun <reified T : Node<*>> Node<*>.allChildrenOfType(): List<T> =
    allChildren.filterIsInstance<T>()

/** Convert a language AST type into its schema type counterpart. */
fun Type<*>.asSchemaType(schema: GraphQLSchema): GraphQLType =
    when (this) {
        is ListType -> GraphQLList.list(requireNotNull(type).asSchemaType(schema))
        is NonNullType -> GraphQLNonNull.nonNull(requireNotNull(type).asSchemaType(schema))
        is TypeName -> requireNotNull(schema.getTypeAs(requireNotNull(name)))
        else -> throw UnsupportedOperationException("Unsupported language type: $this")
    }

/** Convert a schema type into its language AST counterpart. */
fun GraphQLType.asAstType(): Type<*> =
    when (this) {
        is GraphQLList -> ListType(GraphQLTypeUtil.unwrapOneAs<GraphQLInputType>(this).asAstType())
        is GraphQLNonNull -> NonNullType(GraphQLTypeUtil.unwrapOneAs<GraphQLInputType>(this).asAstType())
        is GraphQLNamedType -> TypeName(name)
        else -> throw UnsupportedOperationException("Unsupported schema type: $this")
    }

internal val GraphQLInputType.asReference: GraphQLInputType
    get() =
        when (this) {
            is GraphQLList ->
                GraphQLList((wrappedType as GraphQLInputType).asReference)
            is GraphQLNonNull ->
                GraphQLNonNull((wrappedType as GraphQLInputType).asReference)
            is GraphQLTypeReference -> this
            is GraphQLUnmodifiedType -> GraphQLTypeReference(name)
            else -> throw IllegalArgumentException("Unsupported type: $this")
        }

internal fun TraverserContext<GraphQLSchemaElement>.unlessIntrospection(
    fn: () -> TraversalControl
): TraversalControl {
    val node = thisNode()
    if (node is GraphQLNamedType && node.name.startsWith("__")) {
        return TraversalControl.ABORT
    }
    if (node is GraphQLFieldDefinition && node.name.startsWith("__")) {
        return TraversalControl.ABORT
    }
    return fn()
}

internal fun TraverserContext<GraphQLSchemaElement>.collectTraversedDirectives(): Set<String> =
    parentNodes.mapNotNull { (it as? GraphQLDirective)?.name }.toSet()

internal fun TraverserContext<GraphQLSchemaElement>.containingDirectiveName(): String? =
    parentNodes.firstNotNullOfOrNull { (it as? GraphQLDirective)?.name }

internal fun TraverserContext<GraphQLSchemaElement>.collectContainingInputLikeTypeNames(): Set<String> =
    mutableSetOf<String>().also { names ->
        parentNodes.forEach { element ->
            element.inputLikeTypeName()?.let(names::add)
        }
        thisNode().inputLikeTypeName()?.let(names::add)
    }

internal fun GraphQLSchemaElement.inputLikeTypeName(): String? =
    when (this) {
        is GraphQLInputObjectType -> name
        is GraphQLEnumType -> name
        else -> null
    }
