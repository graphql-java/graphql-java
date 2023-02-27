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
        codeRegistry = codeRegistry.transform({it.dataFetcher(fooTransformedCoordinates, dataFetcher)})
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
                    GraphQLScalarType newNode = node.transform({sc -> sc.name("Bar")})
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
                    GraphQLScalarType newNode = node.transform({sc -> sc.name("Bar")})
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
}
