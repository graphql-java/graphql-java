package graphql.arbitrary

/** An enumeration of GraphQL object flavors */
enum class TypeKind {
    Interface,
    Object,
    Input,
    Union,
    Scalar,
    Enum,
    Directive
}
