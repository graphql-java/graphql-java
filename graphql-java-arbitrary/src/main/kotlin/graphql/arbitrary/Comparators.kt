package graphql.arbitrary

import graphql.ExecutionInput
import graphql.language.Document
import graphql.schema.GraphQLSchema

/** A [Comparator] that orders [Document]s by their node count. */
val DocumentComparator: Comparator<Document> =
    Comparator.comparingInt { it.allChildren.size }

/**
 * A [Comparator] that orders [ExecutionInput]s by how many nodes are in their parsed document.
 *
 * This comparator reparses document text to compare the size of the node trees. Comparing document
 * string lengths would be faster, but node count keeps its behavior consistent with
 * [DocumentComparator].
 */
val ExecutionInputComparator: Comparator<ExecutionInput> =
    Comparator.comparingInt { it.query.asDocument.allChildren.size }

/** A [Comparator] that orders [GraphQLSchema]s by their total type count. */
val GraphQLSchemaComparator: Comparator<GraphQLSchema> =
    Comparator.comparingInt { it.allTypesAsList.size }
