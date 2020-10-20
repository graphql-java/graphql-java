package graphql.schema

import graphql.GraphQL
import graphql.Scalars
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaPrinter
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.schema.FieldCoordinates.coordinates
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLTypeReference.typeRef
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring
import static graphql.util.TreeTransformerUtil.changeNode
import static graphql.util.TreeTransformerUtil.deleteNode

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

    def "can remove field in schema"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
        type Query {
            hello: Foo 
        }
        type Foo {
           bar: String
           baz: String
       } 
        """)
        schema.getQueryType();
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        when:
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "baz") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE;
            }
        })

        then:
        newSchema != schema
        (newSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("baz") == null
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

    def "transform with interface"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
        type Query {
            hello: Foo 
        }
        
        interface Foo {
            bar: String
        }
        
        type FooImpl implements Foo {
           bar: String
           baz: String
        } 
        """)
        schema.getQueryType();
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        when:
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "baz") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE;
            }
        })

        then:
        newSchema != schema
        (newSchema.getType("FooImpl") as GraphQLObjectType).getFieldDefinition("baz") == null
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

            @Override
            TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "Parent") {
                    return changeNode(context, typeRef("ParentChanged"));
                }
                return super.visitGraphQLTypeReference(node, context)
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
  otherParent: ParentChanged
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

    def "traverses all types"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
        type Query {
            hello: Foo 
        }
        
        type Subscription {
            fooHappened: FooEvent
        }
        
        type Mutation {
            updateFoo(foo: FooUpdate): Boolean
        }
        
        type FooEvent {
            foo: Foo
            timestamp: Int
        }
        
        type Foo {
           bar: Bar
        } 
        
        input FooUpdate {
            newBarBaz: String
        }
        
        type Bar {
            baz: String
        }
        
        type Baz {
            bing: String
        }
        
        """)
        SchemaTransformer schemaTransformer = new SchemaTransformer()

        when:
        final Set<String> visitedTypeNames = []
        schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                visitedTypeNames << node.name

                TraversalControl.CONTINUE
            }

            @Override
            TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                visitedTypeNames << node.name

                TraversalControl.CONTINUE
            }
        })

        then:
        visitedTypeNames.containsAll(['Foo', 'FooUpdate', 'FooEvent', 'Bar', 'Baz'])
    }


    def "transformed schema can be executed programmatically"() {

        given:
        // build query and schema manually so we have a test that uses a programmatic approach rather than the SDL.
        def queryObject = newObject()
                .name("Query")
                .field({ builder ->
                    builder.name("foo").type(Scalars.GraphQLString).dataFetcher(new DataFetcher<Object>() {
                        @Override
                        Object get(DataFetchingEnvironment environment) throws Exception {
                            return "bar";
                        }
                    })
                }).build();

        def schemaObject = GraphQLSchema.newSchema()
                .query(queryObject)
                .build()

        when:
        def result = GraphQL.newGraphQL(schemaObject)
                .build().execute('''
            { foo } 
        ''').getData()

        then:
        (result as Map)['foo'] == 'bar'

        when:
        def newSchema = SchemaTransformer.transformSchema(schemaObject, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == 'foo') {
                    def changedNode = node.transform({ builder -> builder.name('fooChanged') })
                    return changeNode(context, changedNode)
                }

                return TraversalControl.CONTINUE
            }
        })
        result = GraphQL.newGraphQL(newSchema)
                .build().execute('''
            { fooChanged }
        ''').getData()

        then:
        (result as Map)['fooChanged'] == 'bar'

    }

    def "transformed schema can be executed"() {

        given:
        GraphQLSchema schema = TestUtil.schema("""
        type Query {
            foo: String
        }
        """, RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("foo",
                                { env ->
                                    return "bar"
                                })
                ).build()
        )

        when:
        def result = GraphQL.newGraphQL(schema)
                .build().execute('''
            { foo } 
        ''').getData()

        then:
        (result as Map)['foo'] == 'bar'

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLCodeRegistry.Builder registryBuilder = context.getVarFromParents(GraphQLCodeRegistry.Builder.class)

                if (node.name == 'foo') {
                    def changedNode = node.transform({ builder -> builder.name('fooChanged') })
                    registryBuilder.dataFetcher(coordinates("Query", "fooChanged"),
                            schema.getCodeRegistry().getDataFetcher(coordinates("Query", "foo"), node))

                    return changeNode(context, changedNode)
                }

                return TraversalControl.CONTINUE
            }
        })
        result = GraphQL.newGraphQL(newSchema)
                .build().execute('''
            { fooChanged }
        ''').getData()

        then:
        (result as Map)['fooChanged'] == 'bar'

    }

    def "type references are replaced again after transformation"() {
        given:
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("account").type(typeRef("Account")).build())
                .build()

        def account = newObject()
                .name("Account")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString).build())
                .field(newFieldDefinition().name("billingStatus").type(typeRef("BillingStatus")).build())
                .build()

        def billingStatus = newObject()
                .name("BillingStatus")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString).build())
                .build()

        def schema = newSchema()
                .query(query)
                .additionalType(billingStatus)
                .additionalType(account)
                .build()
        when:
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "billingStatus") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE;
            }
        })

        then:
        newSchema != schema
        (newSchema.getType("Account") as GraphQLObjectType).getFieldDefinition("billingStatus") == null
        newSchema.getType("Account") == (newSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("account").getType()

    }

    def "test as reported in 1928 "() {
        given:

        def internalNoteHider = new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLDirective(GraphQLDirective node,
                                                   TraverserContext<GraphQLSchemaElement> context) {
                if ("internalnote".equals(node.getName())) {
                    // this deletes the declaration and the two usages of it
                    deleteNode(context);
                }
                return TraversalControl.CONTINUE;
            }
        }

        GraphQLSchema schema = TestUtil.schema("""
            directive @internalnote(doc: String!) on OBJECT | FIELD_DEFINITION | INTERFACE
            
            type Query {
                fooBar: Foo
            }
            
            interface Manchu @internalnote(doc:"...") {
              id: ID!
            }
            
            type Foo implements Manchu {
              id: ID!
            }
            
            type Bar @internalnote(doc:"...") {
              id: ID! 
              hidden: String! 
            }
          
            union FooBar = Foo | Bar
        """)

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, internalNoteHider)

        then:
        newSchema.getType("FooBar") != null
        def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(false))
        then:
        printer.print(newSchema) == """interface Manchu {
  id: ID!
}

union FooBar = Bar | Foo

type Bar {
  hidden: String!
  id: ID!
}

type Foo implements Manchu {
  id: ID!
}

type Query {
  fooBar: Foo
}
"""
    }

    def "test as reported in 1953 "() {
        given:

        def fieldChanger = new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node,
                                                         TraverserContext<GraphQLSchemaElement> context) {
                if (node.getName() == "f") {
                    changeNode(context, node.transform({ builder -> builder.type(Scalars.GraphQLInt) }))
                }
                return TraversalControl.CONTINUE
            }
        }

        GraphQLSchema schema = TestUtil.schema("""
            type Query {
                manchu: Manchu
                foo: Foo
            }
            
            interface Manchu {
              id: ID!
              f: String
            }
            
            type Foo implements Manchu {
              id: ID!
              f: String
            }
        """)

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, fieldChanger)

        def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(false))
        then:
        (newSchema.getType("Foo") as GraphQLObjectType).getFieldDefinition("f").getType() == Scalars.GraphQLInt
        printer.print(newSchema) == """interface Manchu {
  f: Int
  id: ID!
}

type Foo implements Manchu {
  f: Int
  id: ID!
}

type Query {
  foo: Foo
  manchu: Manchu
}
"""
    }
}
