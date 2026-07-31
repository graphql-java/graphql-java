package graphql.schema.property

import graphql.ExecutionInput
import graphql.language.Document
import graphql.parser.Parser

/** Order documents by their AST node count for property-failure minimization. */
internal val DocumentComparator: Comparator<Document> =
    Comparator.comparingInt { it.allChildren.size }

/** Order execution inputs by the AST node count of their query document. */
internal val ExecutionInputComparator: Comparator<ExecutionInput> =
    Comparator.comparingInt { Parser().parseDocument(it.query).allChildren.size }
