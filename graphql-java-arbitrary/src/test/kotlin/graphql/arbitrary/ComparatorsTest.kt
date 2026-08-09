package graphql.arbitrary

import graphql.ExecutionInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComparatorsTest {
    @Test
    fun `DocumentComparator`() {
        val shallow = "{ someVeryLongFieldName }".asDocument
        val equivalent = "{ x }".asDocument
        val deep = "{ x { y } }".asDocument

        assertTrue(DocumentComparator.compare(shallow, deep) < 0)
        assertTrue(DocumentComparator.compare(deep, shallow) > 0)
        assertEquals(0, DocumentComparator.compare(shallow, equivalent))
    }

    @Test
    fun `ExecutionInputComparator`() {
        val shallow = ExecutionInput.newExecutionInput("{ someVeryLongFieldName }").build()
        val equivalent = ExecutionInput.newExecutionInput("{ x }").build()
        val deep = ExecutionInput.newExecutionInput("{ x { y } }").build()

        assertTrue(ExecutionInputComparator.compare(shallow, deep) < 0)
        assertTrue(ExecutionInputComparator.compare(deep, shallow) > 0)
        assertEquals(0, ExecutionInputComparator.compare(shallow, equivalent))
    }

    @Test
    fun `GraphQLSchemaComparator`() {
        val shallow = "type Query { someVeryLongFieldName: Int }".asSchema
        val equivalent = "type Query { x: Int }".asSchema
        val deep = "type Obj { x: Int } type Query { obj: Obj }".asSchema

        assertTrue(GraphQLSchemaComparator.compare(shallow, deep) < 0)
        assertTrue(GraphQLSchemaComparator.compare(deep, shallow) > 0)
        assertEquals(0, GraphQLSchemaComparator.compare(shallow, equivalent))
    }
}
