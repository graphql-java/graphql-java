package graphql.analysis

import graphql.TestUtil
import graphql.language.Document
import graphql.language.Field
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import spock.lang.Specification

import static graphql.language.AstPrinter.printAstCompact
import static graphql.language.AstTransformerUtil.changeNode

class QueryTransformationTraversalTest extends Specification {
    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    QueryTraversal createQueryTraversal(Document document, GraphQLSchema schema, Map variables = [:]) {
        QueryTraversal queryTraversal = QueryTraversal.newQueryTraversal()
                .schema(schema)
                .document(document)
                .variables(variables)
                .build()
        return queryTraversal
    }
    //TODO: after Andy's change with node deletions / additions write test that covers that. Also show how shouldInclude
    //can be used to remove not included part of the query
    def "modify query fields based on type information "() {
        def schema = TestUtil.schema("""
            type Query {
                root: Root
            }
            type Root {
                fooA: Foo
                fooB: Foo  
            }
            type Foo {
                midA: MidA
                midB: MidB
            }
            
            type MidA {
                leafA: String
            }
            type MidB {
                leafB: String
            }
        """)
        def query = TestUtil.parseQuery("{ root { fooA { midA { leafA } midB { leafB } } fooB { midA { leafA } midB { leafB } } } }")

        QueryTraversal queryTraversal = createQueryTraversal(query, schema)

        def visitor = new QueryVisitorWithControlStub() {
            @Override
            TraversalControl visitField(QueryVisitorFieldEnvironment env) {
                if (env.fieldDefinition.type.name == "MidA") {
                    String newName = env.field.name + "-modified"

                    Field changedField = env.field.transform({ builder -> builder.name(newName) })
                    return changeNode(env.getTraverserContext(), changedField)
                }
                return TraversalControl.CONTINUE
            }
        }

        when:
        def newDocument = queryTraversal.transform(visitor)

        then:
        printAstCompact(newDocument) ==
                "query {root {fooA {midA-modified {leafA} midB {leafB}} fooB {midA-modified {leafA} midB {leafB}}}}"
    }
}
