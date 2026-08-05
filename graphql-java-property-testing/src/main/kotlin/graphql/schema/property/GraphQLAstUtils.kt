package graphql.schema.property

import graphql.language.Document
import graphql.language.ListType
import graphql.language.Node
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import graphql.parser.Parser
import graphql.parser.ParserEnvironment
import graphql.parser.ParserOptions
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser

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
