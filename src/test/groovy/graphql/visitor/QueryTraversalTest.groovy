package graphql.visitor

import graphql.TestUtil
import graphql.language.Document
import graphql.language.NodeUtil
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class QueryTraversalTest extends Specification {


    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    QueryTraversal createQueryTraversal(Document document, GraphQLSchema schema, QueryVisitor visitor) {
        def operation = NodeUtil.getOperation(document, null)
        QueryTraversal queryTraversal = new QueryTraversal(
                document,
                operation.operationDefinition,
                schema,
                operation.fragmentsByName,
                [:],
                visitor
        )
        return queryTraversal
    }


    def "simple query"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {bar foo { subFoo} }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, visitor)
        when:
        queryTraversal.traverse()

        then:
        1 * visitor.visitField({ it.name == "foo" }, { it.type.name == "Foo" }, null)
        1 * visitor.visitField({ it.name == "bar" }, { it.type.name == "String" }, null)
        1 * visitor.visitField({ it.name == "subFoo" }, { it.type.name == "String" }, { VisitPath path ->
            path.field.name == "foo" && path.fieldDefinition.type.name == "Foo"
        })
    }

}
