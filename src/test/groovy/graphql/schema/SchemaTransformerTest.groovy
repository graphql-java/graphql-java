package graphql.schema

import graphql.TestUtil
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import graphql.util.TreeTransformerUtil
import spock.lang.Specification

class SchemaTransformerTest extends Specification {


    def "can change field in schema"() {
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
        GraphQLSchema newSchema = schemaTransformer.transformWholeSchema(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "bar") {
                    def changedNode = node.transform({ builder -> builder.name("barChanged") })
                    return TreeTransformerUtil.changeNode(context, changedNode)
                }
                return TraversalControl.CONTINUE;
            }
        })

        then:
        newSchema != schema
        (newSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("barChanged") != null
    }
}
