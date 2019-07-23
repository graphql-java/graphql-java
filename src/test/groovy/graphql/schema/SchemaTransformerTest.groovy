package graphql.schema


import graphql.TestUtil
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLTypeReference.typeRef
import static graphql.util.TreeTransformerUtil.changeNode

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
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "bar") {
                    def changedNode = fieldDefinition.transform({ builder -> builder.name("barChanged") })
                    return changeNode(context, changedNode)
                }
                return TraversalControl.CONTINUE;
            }
        })

        then:
        newSchema != schema
        (newSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("barChanged") != null
    }

    def "can change schema with cycles"() {
        given:
        GraphQLObjectType foo = newObject()
                .name("Foo")
                .field(newFieldDefinition()
                        .name("foo")
                        .type(typeRef("Foo"))
                        .build()
                ).build()
        GraphQLObjectType query = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("queryField")
                        .type(foo)
                        .build()
                ).build()
        GraphQLSchema schema = newSchema()
                .query(query).build()
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        when:
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "foo") {
                    def changedNode = fieldDefinition.transform({ builder -> builder.name("fooChanged") })
                    return changeNode(context, changedNode)
                }
                return TraversalControl.CONTINUE
            }
        })

        then:
        newSchema != schema
        newSchema.typeMap.size() == schema.typeMap.size()
        (newSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("fooChanged") != null
        (newSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("fooChanged").getType() == newSchema.getType("Foo")
    }
}
