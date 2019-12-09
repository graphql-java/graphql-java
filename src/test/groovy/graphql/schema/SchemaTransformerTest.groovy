package graphql.schema


import graphql.TestUtil
import graphql.schema.idl.SchemaPrinter
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

    def "can change schema with logical cycles"() {
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


    def "elements having more than one parent"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
        type Query {
            parent: Parent
        }
        type Parent {
           child1: Child
           child2: Child
           subChild: SubChild
           otherParent: Parent
       }
        type Child {
            hello: String
            subChild: SubChild
        }
        type SubChild {
            hello: String
        }
        """)
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        when:
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "hello") {
                    def changedNode = fieldDefinition.transform({ builder -> builder.name("helloChanged") })
                    return changeNode(context, changedNode)
                }
                return TraversalControl.CONTINUE;
            }

            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "Parent") {
                    def changedNode = node.transform({ builder -> builder.name("ParentChanged") })
                    return changeNode(context, changedNode)
                }
                if (node.name == "SubChild") {
                    def changedNode = node.transform({ builder -> builder.name("SubChildChanged") })
                    return changeNode(context, changedNode)
                }
                return super.visitGraphQLObjectType(node, context)
            }

        })
        def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(false))
        then:
        printer.print(newSchema) == """type Child {
  helloChanged: String
  subChild: SubChildChanged
}

type ParentChanged {
  child1: Child
  child2: Child
  otherParent: Parent
  subChild: SubChildChanged
}

type Query {
  parent: ParentChanged
}

type SubChildChanged {
  helloChanged: String
}
"""
        newSchema != schema
        (newSchema.getType("Child") as GraphQLObjectType).getFieldDefinition("helloChanged") != null


    }

    def "test query, mutation and subscription transformers in schema.builder and field transformers in object type.builder"() {
        when:
            GraphQLSchema schema = TestUtil.schema("""
            schema {
                query: QueryType
            }
            
            type QueryType {
                dummy: String
            }
//            """)
            GraphQLSchema schema2 = schema.transform {
                it.query { it.field("dummy") { it.argument  { it.name("testQuery").type(typeRef("String"))}}}
                .mutation { it.field("testMutation") {it.type(typeRef("Boolean"))}}
                .subscription { it.field("testSubscription") {it.type(typeRef("Boolean"))}}}
        then:
            schema2.queryType?.name == "QueryType"
            schema2.queryType.getFieldDefinition("dummy")?.arguments[0]?.name == "testQuery"
            schema2.mutationType?.name == "Mutation"
            schema2.mutationType.getFieldDefinition("testMutation")?.type instanceof GraphQLScalarType
            schema2.subscriptionType?.name == "Subscription"
            schema2.subscriptionType.getFieldDefinition("testSubscription")?.type instanceof GraphQLScalarType
    }
}
