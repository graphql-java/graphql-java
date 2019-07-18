package graphql.schema


import graphql.TestUtil
import graphql.schema.idl.SchemaPrinter
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import graphql.util.TreeTransformerUtil
import spock.lang.Specification

class SchemaTransformerTest extends Specification {


    def "can change node"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
        type Query {
            hello: Foo 
        }
        type Foo {
           bar: String
       } 
        """)
        schema.getQueryType();
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        when:
        GraphQLObjectType newQuery = schemaTransformer.transform(schema.getQueryType(), new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "hello") {
                    def changedNode = node.transform({ builder -> builder.name("helloChanged") })
                    return TreeTransformerUtil.changeNode(context, changedNode)
                }
                return TraversalControl.CONTINUE;
            }
        })

        then:
        new SchemaPrinter().print(newQuery).trim() == """type Query {
  helloChanged: Foo
}""".trim()
    }
}
