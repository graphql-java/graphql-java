package graphql.schema


import graphql.AssertException
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
        schema.getQueryType()
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        when:
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "bar") {
                    def changedNode = fieldDefinition.transform({ builder -> builder.name("barChanged") })
                    return changeNode(context, changedNode)
                }
                return TraversalControl.CONTINUE
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
        schema.getQueryType()
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        when:
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "baz") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
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
        schema.getQueryType()
        SchemaTransformer schemaTransformer = new SchemaTransformer()
        when:
        GraphQLSchema newSchema = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {
                if (fieldDefinition.name == "baz") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
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
                return TraversalControl.CONTINUE
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
                    return changeNode(context, typeRef("ParentChanged"))
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
                    builder.name("foo")
                            .type(Scalars.GraphQLString)
                }).build()

        def fooCoordinates = FieldCoordinates.coordinates("Query", "foo")
        DataFetcher<?> dataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return "bar"
            }
        }
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, dataFetcher)
                .build()

        def schemaObject = newSchema()
                .codeRegistry(codeRegistry)
                .query(queryObject)
                .build()

        when:
        def result = GraphQL.newGraphQL(schemaObject)
                .build()
                .execute('''
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

        def fooTransformedCoordinates = FieldCoordinates.coordinates("Query", "fooChanged")
        codeRegistry = codeRegistry.transform({ it.dataFetcher(fooTransformedCoordinates, dataFetcher) })
        newSchema = newSchema.transform({
            builder -> builder.codeRegistry(codeRegistry)
        })
        result = GraphQL.newGraphQL(newSchema)
                .build()
                .execute('''
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
                return TraversalControl.CONTINUE
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
                if ("internalnote" == node.getName()) {
                    // this deletes the declaration and the two usages of it
                    deleteNode(context)
                }
                return TraversalControl.CONTINUE
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

    def "can change a schema element only"() {
        def sdl = '''
            type Query {
                f : Foo
            }
            type Foo {
                foo : Foo
                bar : Bar
            }
            type Bar {
                b : EnumType
            }
            enum EnumType {
              E
            }
        '''
        def schema = TestUtil.schema(sdl)
        def oldType = schema.getObjectType("Foo")
        when:
        GraphQLObjectType newType = new SchemaTransformer().transform(oldType, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                node = node.transform({ b -> b.name(node.getName().toUpperCase()) })
                return changeNode(context, node)
            }

            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                node = node.transform({ b -> b.name(node.getName().toUpperCase()) })
                return changeNode(context, node)
            }
        })
        then:
        newType.getName() == "FOO"
        newType.getFieldDefinition("FOO") != null
        newType.getFieldDefinition("BAR") != null
    }

    def "can handle self referencing type which require type references"() {
        def sdl = '''
            type Query {
                f : Foo
            }
            type Foo {
                foo : Foo
                bar : Bar
            }
            type Bar {
                foo : Foo
                enum : EnumType
            }
            enum EnumType {
              e
            }
        '''
        def schema = TestUtil.schema(sdl)

        when:
        GraphQLSchema newSchema = new SchemaTransformer().transform(schema, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                node = node.transform({ b -> b.name(node.getName().toUpperCase()) })
                return changeNode(context, node)
            }

            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.getName().startsWith("__")) return TraversalControl.ABORT
                node = node.transform({ b -> b.name(node.getName().toUpperCase()) })
                return changeNode(context, node)
            }
        })
        then:

        // all our fields are upper case as are our object types
        def queryType = newSchema.getObjectType("QUERY")
        def fooType = newSchema.getObjectType("FOO")
        def barType = newSchema.getObjectType("BAR")
        def enumType = newSchema.getType("EnumType") as GraphQLEnumType

        queryType.getFieldDefinition("F").getType().is(fooType) // groovy object equality
        fooType.getFieldDefinition("FOO").getType().is(fooType)
        fooType.getFieldDefinition("BAR").getType().is(barType)

        barType.getFieldDefinition("FOO").getType().is(fooType)
        barType.getFieldDefinition("ENUM").getType().is(enumType)

        enumType.getValue("e") != null // left alone
    }

    def "cycle with type refs"() {
        given:
        def field = newFieldDefinition()
                .name("foo")
                .type(typeRef("Foo"))
                .build()

        def query = newObject()
                .name("Query")
                .field(field)
                .build()
        def foo = newObject()
                .name("Foo")
                .field(newFieldDefinition().name("toChange").type(Scalars.GraphQLString))
                .field(newFieldDefinition().name("subFoo").type(typeRef("Foo")))
                .build()


        GraphQLSchema schema = newSchema().query(query).additionalType(foo).build()
        def fieldChanger = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node,
                                                         TraverserContext<GraphQLSchemaElement> context) {
                if (node.getName() == "toChange") {
                    changeNode(context, node.transform({ builder -> builder.name("changed") }))
                }
                return TraversalControl.CONTINUE
            }
        }

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, fieldChanger)

        def newFoo = newSchema.getQueryType().getFieldDefinition("foo").getType() as GraphQLObjectType
        then:
        newFoo.getFieldDefinition("changed") != null
    }

    def "delete type which is references twice"() {
        def sdl = '''
            type Query {
                u1: U1
                u2: U2
            }
            union U1 = A | ToDel 
            union U2 = B | ToDel
            type A {
                a: String
            }
            type B {
                a: String
            }
            type ToDel {
                a: String
            }
            
        '''
        def schema = TestUtil.schema(sdl)

        when:
        GraphQLSchema newSchema = new SchemaTransformer().transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.getName() == 'ToDel') {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
            }
        })
        then:
        def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(false))
        printer.print(newSchema) == '''union U1 = A

union U2 = B

type A {
  a: String
}

type B {
  a: String
}

type Query {
  u1: U1
  u2: U2
}
'''
    }

    def "if nothing changes in the schema transformer, we return the same object"() {

        def schema = TestUtil.schema("type Query { f : String }")

        def fieldChanger = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node,
                                                         TraverserContext<GraphQLSchemaElement> context) {
                return TraversalControl.CONTINUE
            }
        }

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, fieldChanger)
        then:
        newSchema === schema
    }

    def "__Field can be changed"() {
        // this is a test when only
        // one element inside a scc is changed
        def schema = TestUtil.schema("type Query { f : String }")

        def fieldChanger = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "__Field") {
                    return changeNode(context, node.transform({ it.name("__FieldChanged") }))
                }
                return TraversalControl.CONTINUE
            }
        }

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, fieldChanger)
        then:
        def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(false))
        printer.print(newSchema) == '''type Query {
  f: String
}
'''
        newSchema.getObjectType("__FieldChanged") != null
        newSchema.getObjectType("__Field") == null

    }


    def "applied directive and applied args can be changed"() {
        // this is a test when only
        // one element inside a scc is changed
        def schema = TestUtil.schema("""
            directive @foo(arg1 : String) on FIELD_DEFINITION
            directive @bar(arg1 : String) on FIELD_DEFINITION
            type Query {
                field : String @foo(arg1 : "fooArg")
                field2 : String @bar(arg1 : "barArg")
            }
""")

        def visitor = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
                if (context.getParentNode() instanceof GraphQLAppliedDirective) {
                    GraphQLAppliedDirective directive = context.getParentNode()
                    if (directive.name == "foo") {
                        if (node.name == "arg1") {
                            def newNode = node.transform({
                                it.name("changedArg1")
                            })
                            return changeNode(context, newNode)
                        }
                    }
                }
                return TraversalControl.CONTINUE
            }

            @Override
            TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective node, TraverserContext<GraphQLSchemaElement> context) {
                return super.visitGraphQLAppliedDirective(node, context)
            }

        }

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, visitor)
        then:
        def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(true))
        def newQueryType = newSchema.getObjectType("Query")

        printer.print(newQueryType) == '''type Query {
  field: String @foo(changedArg1 : "fooArg")
  field2: String @bar(arg1 : "barArg")
}
'''
    }

    def "can rename scalars"() {

        def schema = TestUtil.schema("""
            scalar Foo
            type Query {
                field : Foo
            }
""")

        def visitor = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.getName() == "Foo") {
                    GraphQLScalarType newNode = node.transform({ sc -> sc.name("Bar") })
                    return changeNode(context, newNode)
                }
                return super.visitGraphQLScalarType(node, context)
            }
        }

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, visitor)
        then:
        newSchema.getType("Bar") instanceof GraphQLScalarType
        newSchema.getType("Foo") == null
        (newSchema.getObjectType("Query").getFieldDefinition("field").getType() as GraphQLScalarType).getName() == "Bar"
    }

    def "rename scalars are changed in applied arguments"() {

        def schema = TestUtil.schema("""
            scalar Foo
            directive @myDirective(fooArgOnDirective: Foo) on FIELD_DEFINITION
            type Query {
              foo(fooArgOnField: Foo) : Foo @myDirective
            }
""")

        def visitor = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.getName() == "Foo") {
                    GraphQLScalarType newNode = node.transform({ sc -> sc.name("Bar") })
                    return changeNode(context, newNode)
                }
                return super.visitGraphQLScalarType(node, context)
            }
        }

        when:
        def newSchema = SchemaTransformer.transformSchema(schema, visitor)
        then:

        def fieldDef = newSchema.getObjectType("Query").getFieldDefinition("foo")
        def appliedDirective = fieldDef.getAppliedDirective("myDirective")
        def oldSkoolDirective = fieldDef.getDirective("myDirective")
        def argument = fieldDef.getArgument("fooArgOnField")
        def directiveDecl = newSchema.getDirective("myDirective")
        def directiveArgument = directiveDecl.getArgument("fooArgOnDirective")

        (fieldDef.getType() as GraphQLScalarType).getName() == "Bar"
        (argument.getType() as GraphQLScalarType).getName() == "Bar"
        (directiveArgument.getType() as GraphQLScalarType).getName() == "Bar"

        (oldSkoolDirective.getArgument("fooArgOnDirective").getType() as GraphQLScalarType).getName() == "Bar"

        newSchema.getType("Bar") instanceof GraphQLScalarType

        // not working at this stage
        (appliedDirective.getArgument("fooArgOnDirective").getType() as GraphQLScalarType).getName() == "Bar"
        newSchema.getType("Foo") == null
    }

    def "has access to common variables"() {
        def schema = TestUtil.schema("""
            type Query {
              foo : String
            }
        """)

        def visitedSchema = null
        def visitedCodeRegistry = null
        def visitor = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                visitedSchema = context.getVarFromParents(GraphQLSchema.class)
                visitedCodeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class)
                return super.visitGraphQLFieldDefinition(node, context)
            }

        }

        when:
        SchemaTransformer.transformSchema(schema, visitor)

        then:
        visitedSchema == schema
        visitedCodeRegistry instanceof GraphQLCodeRegistry.Builder
    }

    def "deprecation transformation correctly overrides existing deprecated directive reasons"() {
        def schema = TestUtil.schema("""
          schema {
            query: QueryType
          }
                
          type QueryType {
            a: String
            b: String @deprecated(reason: "Replace this doc")
          }
          
          interface InterfaceType {
            a: String
            b: String @deprecated(reason: "Replace this doc")
          }
          
          input InputType {
            a: String
            b: String @deprecated(reason: "Replace this doc")
          }
        """)

        when:
        def typeVisitor = new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                def n = node.transform(b -> b.deprecate("NEW REASON"));
                return changeNode(context, n);
            }

            @Override
            TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
                def n = node.transform(b -> b.deprecate("NEW REASON"));
                return changeNode(context, n);
            }
        }
        def newSchema = SchemaTransformer.transformSchema(schema, typeVisitor)

        then:
        def newQueryType = newSchema.getObjectType("QueryType")
        def newQueryTypePrinted = new SchemaPrinter().print(newQueryType)

        newQueryTypePrinted == """type QueryType {
  a: String @deprecated(reason : "NEW REASON")
  b: String @deprecated(reason : "NEW REASON")
}
"""
        def newInterfaceType = newSchema.getType("InterfaceType")
        def newInterfaceTypePrinted = new SchemaPrinter().print(newInterfaceType)
        newInterfaceTypePrinted == """interface InterfaceType {
  a: String @deprecated(reason : "NEW REASON")
  b: String @deprecated(reason : "NEW REASON")
}
"""
        def newInputType = newSchema.getType("InputType")
        def newInputTypePrinted = new SchemaPrinter().print(newInputType)
        newInputTypePrinted == """input InputType {
  a: String @deprecated(reason : "NEW REASON")
  b: String @deprecated(reason : "NEW REASON")
}
"""
    }

    def "issue 4133 reproduction"() {
        def sdl = """
            directive @remove on FIELD_DEFINITION
            
            type Query {
              rental: Rental @remove
              customer: Customer
            }
            
            type Store {
              inventory: Inventory @remove
            }
            
            type Inventory {
              store: Store @remove
            }
            
            type Customer {
              rental: Rental
              payment: Payment @remove
            }
            
            type Payment {
              inventory: Inventory @remove
            }
            
            type Rental {
              id: ID
              customer: Customer @remove
            }
        """

        def schema = TestUtil.schema(sdl)

        def visitor = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.hasAppliedDirective("remove")) {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
            }

            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.getFields().stream().allMatch(field -> field.hasAppliedDirective("remove"))) {
                    return deleteNode(context)
                }

                return TraversalControl.CONTINUE
            }
        }
        when:
        def newSchema = SchemaTransformer.transformSchemaWithDeletes(schema, visitor)
        def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(false))
        def newSdl = printer.print(newSchema)

        then:
        newSdl.trim() == """type Customer {
  rental: Rental
}

type Query {
  customer: Customer
}

type Rental {
  id: ID
}""".trim()
    }

    /**
     * This test verifies the fix for issue 4133: deleting nodes with circular references.
     * <p>
     * <h3>The Problem (Fixed)</h3>
     * When a node is deleted via {@code deleteNode(context)}, the traversal does NOT continue to visit
     * the children of that deleted node (see {@code TraverserState.pushAll()}). This was problematic
     * when combined with how GraphQL-Java handles circular type references using {@code GraphQLTypeReference}.
     * <p>
     * <h3>How GraphQLTypeReference Creates Asymmetry</h3>
     * In circular references, one direction uses the actual type object, while the other uses a
     * {@code GraphQLTypeReference} (a placeholder resolved during schema building):
     * <ul>
     *   <li>{@code Customer.rental} → {@code GraphQLTypeReference("Rental")} (placeholder)</li>
     *   <li>{@code Rental.customer} → {@code Customer} (actual object reference)</li>
     * </ul>
     * <p>
     * <h3>Traversal in This Test</h3>
     * <ol>
     *   <li>{@code Query.rental} field is visited → has @remove → DELETED → children NOT traversed</li>
     *   <li>{@code Rental} type is NOT visited via this path (it's a child of the deleted field)</li>
     *   <li>{@code Query.customer} field is visited → {@code Customer} type IS visited</li>
     *   <li>{@code Customer.rental} field is visited → but its type is {@code GraphQLTypeReference("Rental")}</li>
     *   <li>The actual {@code Rental} GraphQLObjectType would NOT be visited (only referenced by name)</li>
     *   <li>{@code Customer.payment} field is visited → has @remove → DELETED → {@code Payment} NOT visited</li>
     *   <li>{@code Inventory} would NOT be visited (only reachable through Payment)</li>
     * </ol>
     * <p>
     * <h3>The Fix</h3>
     * {@code SchemaTransformer.transformSchemaWithDeletes()} ensures all types are visited by temporarily
     * adding them as extra root types during transformation. Types that are modified are then included
     * in the rebuilt schema's additionalTypes, ensuring type references are properly resolved.
     */
    def "issue 4133 - circular references with deletes - fixed with transformSchemaWithDeletes"() {
        def sdl = """
            directive @remove on FIELD_DEFINITION
            
            type Query {
              rental: Rental @remove
              customer: Customer
            }
            
            type Customer {
              rental: Rental
              payment: Payment @remove
            }
            
            type Rental {
              id: ID
              customer: Customer @remove
            }
            
            type Payment {
              inventory: Inventory @remove
            }
            
            type Inventory {
              payment: Payment @remove
            }
        """

        def schema = TestUtil.schema(sdl)

        def visitor = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.hasAppliedDirective("remove")) {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
            }

            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.getFields().stream().allMatch(field -> field.hasAppliedDirective("remove"))) {
                    return deleteNode(context)
                }

                return TraversalControl.CONTINUE
            }
        }
        when:
        // Use the new transformSchemaWithDeletes method - this should work!
        def newSchema = SchemaTransformer.transformSchemaWithDeletes(schema, visitor)
        def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeDirectives(false))
        def newSdl = printer.print(newSchema)

        then:
        newSdl.trim() == """type Customer {
  rental: Rental
}

type Query {
  customer: Customer
}

type Rental {
  id: ID
}""".trim()
    }

    def "indirect type references should have their children collected"() {
        given:
        // Bar is referenced by Foo.bar directly
        def bar = newObject()
                .name("Bar")
                .field(newFieldDefinition()
                        .name("id")
                        .type(Scalars.GraphQLID)
                        .build())
                .build()

        // Foo references Bar directly via Foo.bar field
        def foo = newObject()
                .name("Foo")
                .field(newFieldDefinition()
                        .name("bar")
                        .type(bar)  // Direct reference to Bar
                        .build())
                .build()

        // Query.foo1 references Foo via type reference (indirect)
        // Query.foo2 references Foo directly (strong reference)
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("foo1")
                        .type(typeRef("Foo"))  // Indirect reference via typeRef
                        .build())
                .field(newFieldDefinition()
                        .name("foo2")
                        .type(foo)  // Direct reference to Foo
                        .build())
                .build()

        def schema = newSchema()
                .query(query)
                .build()

        // Visitor that removes Query.foo2
        def visitor = new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "foo2") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
            }
        }

        when:
        def newSchema = SchemaTransformer.transformSchemaWithDeletes(schema, visitor)

        then: "Query.foo2 should be removed"
        (newSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("foo2") == null

        and: "Query.foo1 should still exist"
        (newSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("foo1") != null

        and: "Foo should still exist (reachable via Query.foo1)"
        newSchema.getType("Foo") != null

        and: "Bar should still exist (reachable via Query.foo1 -> Foo -> bar)"
        newSchema.getType("Bar") != null
    }

    def "nested indirect type references requiring multiple traversals should have their children collected"() {
        given:
        // Create a deeply nested structure where each level has indirect references:
        // Query.level1 -> Level1 (via typeRef) -> Level2 (direct) -> Level3 (via typeRef) -> Level4 (direct) -> Leaf (direct)
        // Query.directRef -> Level1 (direct) - this is the only direct path
        // When we remove Query.directRef, the nested traversals should still find all types

        def leaf = newObject()
                .name("Leaf")
                .field(newFieldDefinition()
                        .name("value")
                        .type(Scalars.GraphQLString)
                        .build())
                .build()

        def level4 = newObject()
                .name("Level4")
                .field(newFieldDefinition()
                        .name("leaf")
                        .type(leaf)  // Direct reference to Leaf
                        .build())
                .build()

        def level3 = newObject()
                .name("Level3")
                .field(newFieldDefinition()
                        .name("level4")
                        .type(level4)  // Direct reference to Level4
                        .build())
                .build()

        def level2 = newObject()
                .name("Level2")
                .field(newFieldDefinition()
                        .name("level3")
                        .type(typeRef("Level3"))  // Indirect reference via typeRef
                        .build())
                .field(newFieldDefinition()
                        .name("level3Direct")
                        .type(level3)  // Direct reference to Level3 (needed for schema build)
                        .build())
                .build()

        def level1 = newObject()
                .name("Level1")
                .field(newFieldDefinition()
                        .name("level2")
                        .type(level2)  // Direct reference to Level2
                        .build())
                .build()

        def query = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("level1Indirect")
                        .type(typeRef("Level1"))  // Indirect reference via typeRef
                        .build())
                .field(newFieldDefinition()
                        .name("level1Direct")
                        .type(level1)  // Direct reference to Level1
                        .build())
                .build()

        def schema = newSchema()
                .query(query)
                .build()

        // Visitor that removes Query.level1Direct and Level2.level3Direct
        // This leaves only indirect paths: Query.level1Indirect -> Level1 -> Level2.level3 -> Level3 -> Level4 -> Leaf
        def visitor = new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "level1Direct" || node.name == "level3Direct") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
            }
        }

        when:
        def newSchema = SchemaTransformer.transformSchemaWithDeletes(schema, visitor)

        then: "Direct fields should be removed"
        (newSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("level1Direct") == null
        (newSchema.getType("Level2") as GraphQLObjectType).getFieldDefinition("level3Direct") == null

        and: "Indirect fields should still exist"
        (newSchema.getType("Query") as GraphQLObjectType).getFieldDefinition("level1Indirect") != null
        (newSchema.getType("Level2") as GraphQLObjectType).getFieldDefinition("level3") != null

        and: "All types in the chain should still exist (discovered through nested indirect reference traversal)"
        newSchema.getType("Level1") != null
        newSchema.getType("Level2") != null
        newSchema.getType("Level3") != null
        newSchema.getType("Level4") != null
        newSchema.getType("Leaf") != null
    }

    def "redefined types are caught when introduced during transformation and discovered through indirect references"() {
        given:
        // Build a valid schema where:
        // - Query.fooIndirect -> Foo (via typeRef)
        // - Query.fooDirect -> Foo (direct) - will be removed during transformation
        // - Foo.targetType -> TargetType (direct) - will be REPLACED during transformation
        // - Query.existingType -> ExistingType (direct) - already in schema
        //
        // During transformation, we will:
        // 1. Remove Query.fooDirect (so Foo is only reachable via indirect reference)
        // 2. Replace TargetType with a NEW object also named "ExistingType" (introduces duplicate)
        //
        // When fixDanglingReplacedTypes traverses from Foo (indirect reference),
        // it should discover the replaced type and detect the duplicate with ExistingType

        def targetType = newObject()
                .name("TargetType")
                .field(newFieldDefinition()
                        .name("id")
                        .type(Scalars.GraphQLID)
                        .build())
                .build()

        def existingType = newObject()
                .name("ExistingType")
                .field(newFieldDefinition()
                        .name("name")
                        .type(Scalars.GraphQLString)
                        .build())
                .build()

        def foo = newObject()
                .name("Foo")
                .field(newFieldDefinition()
                        .name("targetType")
                        .type(targetType)
                        .build())
                .build()

        def query = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("fooIndirect")
                        .type(typeRef("Foo"))  // Indirect reference
                        .build())
                .field(newFieldDefinition()
                        .name("fooDirect")
                        .type(foo)  // Direct reference - will be removed
                        .build())
                .field(newFieldDefinition()
                        .name("existingType")
                        .type(existingType)  // Direct reference to ExistingType
                        .build())
                .build()

        def schema = newSchema()
                .query(query)
                .build()

        // Create a duplicate type with the same name as ExistingType but different instance
        def duplicateExistingType = newObject()
                .name("ExistingType")
                .field(newFieldDefinition()
                        .name("differentField")  // Different field makes it a different object
                        .type(Scalars.GraphQLInt)
                        .build())
                .build()

        // Visitor that:
        // 1. Removes Query.fooDirect (so Foo is only reachable via indirect reference)
        // 2. Replaces TargetType with duplicateExistingType (introduces a duplicate "ExistingType")
        def visitor = new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "fooDirect") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
            }

            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.name == "TargetType") {
                    // Replace TargetType with a type named "ExistingType" (duplicate!)
                    return changeNode(context, duplicateExistingType)
                }
                return TraversalControl.CONTINUE
            }
        }

        when:
        // This should fail because:
        // 1. After removing fooDirect, Foo is only reachable via fooIndirect (typeRef)
        // 2. fixDanglingReplacedTypes traverses from Foo
        // 3. It discovers the replaced type (now named "ExistingType")
        // 4. This conflicts with the already-collected ExistingType from Query.existingType
        SchemaTransformer.transformSchemaWithDeletes(schema, visitor)

        then:
        def e = thrown(AssertException)
        e.getMessage().contains("All types within a GraphQL schema must have unique names")
        e.getMessage().contains("ExistingType")
    }

    def "can modify a built-in directive via schema transformation"() {
        given:
        GraphQLSchema schema = TestUtil.schema("""
            type Query {
                hello: String @deprecated(reason: "use goodbye")
                goodbye: String
            }
        """)

        when:
        GraphQLSchema newSchema = SchemaTransformer.transformSchema(schema, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.getName() == "deprecated") {
                    def changedNode = node.transform({ builder ->
                        builder.argument(GraphQLArgument.newArgument()
                                .name("deletionDate")
                                .type(Scalars.GraphQLString)
                                .description("The date when this field will be removed"))
                    })
                    return changeNode(context, changedNode)
                }
                return TraversalControl.CONTINUE
            }
        })

        then: "the modified built-in directive has the new argument"
        def deprecatedDirective = newSchema.getDirective("deprecated")
        deprecatedDirective != null
        deprecatedDirective.getArguments().size() == 2
        deprecatedDirective.getArgument("reason") != null
        deprecatedDirective.getArgument("deletionDate") != null
        deprecatedDirective.getArgument("deletionDate").getType() == Scalars.GraphQLString

        and: "other built-in directives remain unchanged"
        newSchema.getDirective("include").getArguments().size() == 1
        newSchema.getDirective("skip").getArguments().size() == 1

        and: "all built-in directives are still present"
        newSchema.getDirective("include") != null
        newSchema.getDirective("skip") != null
        newSchema.getDirective("deprecated") != null
        newSchema.getDirective("specifiedBy") != null
        newSchema.getDirective("oneOf") != null
        newSchema.getDirective("defer") != null
        newSchema.getDirective("experimental_disableErrorPropagation") != null
        newSchema.getDirectives().size() == schema.getDirectives().size()
    }
}
