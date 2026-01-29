package graphql.schema

import graphql.AssertException
import graphql.Directives
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.Directive
import graphql.language.SchemaExtensionDefinition
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import java.util.function.UnaryOperator

import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.characterInterface
import static graphql.StarWarsSchema.droidType
import static graphql.StarWarsSchema.humanType
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLTypeReference.typeRef
import static java.util.stream.Collectors.toList

class GraphQLSchemaTest extends Specification {

    def "getImplementations works as expected"() {
        when:
        List<GraphQLObjectType> objectTypes = starWarsSchema.getImplementations(characterInterface)

        then:
        objectTypes.size() == 2
        objectTypes == [
                droidType, humanType
        ]

    }

    def "isPossibleType works as expected"() {
        expect:
        starWarsSchema.isPossibleType(characterInterface, humanType)
    }

    def "isPossibleType when wrong abstract type is passed expect exception"() {
        when:
        starWarsSchema.isPossibleType(humanType, humanType)
        then:
        thrown(AssertException)
    }

    def "#698 interfaces copied as expected"() {

        def idl = """
            type Query {
              foo: Node
            }
            
            interface Node {
              id: String
            }
            
            type Foo implements Node {
              id: String
            }
        """

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", { wiring ->
                    wiring.dataFetcher("foo", { env ->
                        Map<String, Object> map = new HashMap<>()
                        map.put("id", "abc")
                        return map
                    })
                } as UnaryOperator<TypeRuntimeWiring.Builder>)
                .type("Node", { wiring ->
                    wiring.typeResolver({ env -> (GraphQLObjectType) env.getSchema().getType("Foo") })
                } as UnaryOperator<TypeRuntimeWiring.Builder>)
                .build()

        def existingSchema = TestUtil.schema(idl, runtimeWiring)


        GraphQLSchema schema = existingSchema.transform({})

        expect:
        assert 0 == runQuery(existingSchema).getErrors().size()
        assert 0 == runQuery(schema).getErrors().size()
    }

    static def runQuery(GraphQLSchema schema) {
        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .build()

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("{foo {id}}")
                .build()

        return graphQL
                .executeAsync(executionInput)
                .join()
    }

    static def basicSchemaBuilder() {
        def queryTypeName = "QueryType"
        def fooCoordinates = FieldCoordinates.coordinates(queryTypeName, "hero")
        DataFetcher<?> basicDataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return null
            }
        }

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(fooCoordinates, basicDataFetcher)
                .build()

        GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name("QueryType")
                        .field(newFieldDefinition()
                                .name("hero")
                                .type(GraphQLString)
                ))
    }

    def "schema builder copies extension definitions"() {
        setup:
        def schemaBuilder = basicSchemaBuilder()
        def newDirective = Directive.newDirective().name("pizza").build()
        def extension = SchemaExtensionDefinition.newSchemaExtensionDefinition().directive(newDirective).build()
        def oldSchema = schemaBuilder.extensionDefinitions([extension]).build()

        when:
        def newSchema = GraphQLSchema.newSchema(oldSchema).build()

        then:
        oldSchema.extensionDefinitions.size() == 1
        newSchema.extensionDefinitions.size() == 1
        ((Directive) oldSchema.extensionDefinitions.first().getDirectives().first()).name == "pizza"
        ((Directive) newSchema.extensionDefinitions.first().getDirectives().first()).name == "pizza"
    }

    def "all built-in directives are always present"() {
        setup:
        def schemaBuilder = basicSchemaBuilder()

        when: "a schema is built"
        def schema = schemaBuilder.build()
        then: "all 7 built-in directives are present"
        schema.directives.size() == 7
        schema.getDirective("include") != null
        schema.getDirective("skip") != null
        schema.getDirective("deprecated") != null
        schema.getDirective("specifiedBy") != null
        schema.getDirective("oneOf") != null
        schema.getDirective("defer") != null
        schema.getDirective("experimental_disableErrorPropagation") != null

        when: "the schema is transformed, things are copied"
        schema = schema.transform({ builder -> builder })
        then: "all 7 built-in directives are still present"
        schema.directives.size() == 7
    }

    def "clear additional types works as expected"() {
        setup:
        def schemaBuilder = basicSchemaBuilder()

        when: "no additional types have been specified"
        def schema = schemaBuilder.build()
        then:
        schema.additionalTypes.size() == 0

        when: "clear types is called"
        schema = schemaBuilder.clearAdditionalTypes().build()
        then:
        schema.additionalTypes.empty

        when: "clear types is called with additional types"
        def additional1TypeName = "Additional1"
        def additional2TypeName = "Additional2"
        def fieldName = "field"
        def additionalType1 = newObject()
                .name(additional1TypeName)
                .field(newFieldDefinition()
                        .name(fieldName)
                        .type(GraphQLString))
                .build()
        def additionalType2 = newObject()
                .name("Additional2")
                .field(newFieldDefinition()
                        .name(fieldName)
                        .type(GraphQLString))
                .build()

        def additional1Coordinates = FieldCoordinates.coordinates(additionalType1, fieldName)
        DataFetcher<?> basicDataFetcher = new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return null
            }
        }

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(additional1Coordinates, basicDataFetcher)
                .build()

        schema = schemaBuilder
                .clearAdditionalTypes()
                .additionalType(additionalType1)
                .codeRegistry(codeRegistry)
                .build()

        then:
        schema.additionalTypes.size() == 1

        when: "the schema is transformed, things are copied"
        def additional2Coordinates = FieldCoordinates.coordinates(additional2TypeName, fieldName)
        codeRegistry = codeRegistry.transform({ builder -> builder.dataFetcher(additional2Coordinates, basicDataFetcher) })
        schema = schema.transform({ builder -> builder.additionalType(additionalType2).codeRegistry(codeRegistry) })
        then:
        schema.additionalTypes.size() == 2
    }

    def "getType works as expected"() {
        def sdl = '''
        type Query {
            field1 : Pet
            field2 : UnionType
        }
        
        interface Pet {
            name : String
        }
        
        type Dog implements Pet {
            name : String
        }
        type Cat implements Pet {
            name : String
        }
            
        union UnionType = Cat | Dog
        '''

        when:
        def schema = TestUtil.schema(sdl)

        then:
        schema.containsType("Pet")
        schema.containsType("Dog")
        !schema.containsType("Elephant")

        schema.getType("Pet") != null

        GraphQLInterfaceType petType = schema.getTypeAs("Pet")
        petType.getName() == "Pet"

        GraphQLObjectType dogType = schema.getTypeAs("Dog")
        dogType.getName() == "Dog"
    }

    def "issue with type references when original type is transformed away"() {
        def sdl = '''
            type Query {
              # The b fields leads to the addition of the B type (actual definition)
              b: B
              # When we filter out the `b` field, we can still access B through A
              # however they are GraphQLTypeReferences and not an actual GraphQL Object
              a: A
            } 

            type A {
              b: B
            }

            type B {
              a: A
              b: B
            }
        '''

        when:
        def schema = TestUtil.schema(sdl)
        // When field `b` is filtered out
        List<GraphQLFieldDefinition> fields = schema.queryType.fieldDefinitions.stream().filter({
            it.name == "a"
        }).collect(toList())
        // And we transform the schema's query root, the schema building
        // will throw because type B won't be in the type map anymore, since
        // there are no more actual B object types in the schema tree.
        def transformed = schema.transform({
            it.query(schema.queryType.transform({
                it.replaceFields(fields)
            }))
        })

        then:
        transformed.containsType("B")

    }

    def "can change types via SchemaTransformer and visitor"() {
        def sdl = '''
            type Query {
              b: B
              a: A
            } 

            type A {
              b: B
            }

            type B {
              a: A
              b: B
            }
        '''

        when:
        def schema = TestUtil.schema(sdl)

        GraphQLTypeVisitorStub visitor = new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLObjectType(GraphQLObjectType objectType, TraverserContext<GraphQLSchemaElement> context) {
                if (objectType.getName() == "Query") {
                    def queryType = objectType
                    List<GraphQLFieldDefinition> fields = queryType.fieldDefinitions.stream().filter({
                        it.name == "a"
                    }).collect(toList())

                    GraphQLObjectType newObjectType = queryType.transform({
                        it.replaceFields(fields)
                    })

                    return changeNode(context, newObjectType)
                }
                return TraversalControl.CONTINUE
            }
        }
        GraphQLSchema transformedSchema = SchemaTransformer.transformSchema(schema, visitor)

        then:
        transformedSchema.containsType("B")
    }

    def "fields edited from type references should still built valid schemas"() {
        def typeB = newObject().name("B")
                .field(newFieldDefinition().name("a").type(typeRef("A")))
                .field(newFieldDefinition().name("b").type(typeRef("B")))
                .build()


        def typeAFieldB = newFieldDefinition().name("b").type(typeRef("B")).build()
        // at the line above typeB is never strongly referenced
        // and this simulates an edit situation that wont happen with direct java declaration
        // but where the type reference is replaced to an actual but its the ONLY direct reference
        // to that type``
        typeAFieldB.replaceType(typeB)

        def typeA = newObject().name("A")
                .field(typeAFieldB)
                .build()

        //
        // the same pattern above applies ot other replaceable types like arguments and input fields
        def inputTypeY = newInputObject().name("InputTypeY")
                .field(newInputObjectField().name("in2").type(GraphQLString))
                .build()

        def inputFieldOfY = newInputObjectField().name("inY").type(typeRef("InputTypeY")).build()
        // only reference to InputTypeY
        inputFieldOfY.replaceType(inputTypeY)

        def inputTypeZ = newInputObject().name("InputTypeZ")
                .field(inputFieldOfY)
                .build()

        def inputTypeX = newInputObject().name("InputTypeX")
                .field(newInputObjectField().name("inX").type(GraphQLString))
                .build()

        GraphQLArgument argOfX = newArgument().name("argOfX").type(typeRef("InputTypeX")).build()
        // only reference to InputTypeX
        argOfX.replaceType(inputTypeX)

        GraphQLArgument argOfZ = newArgument().name("argOfZ").type(inputTypeZ).build()

        def typeC = newObject().name("C")
                .field(newFieldDefinition().name("f1").type(GraphQLString).argument(argOfX))
                .field(newFieldDefinition().name("f2").type(GraphQLString).argument(argOfZ))
                .build()

        def queryType = newObject().name("Query")
                .field(newFieldDefinition().name("a").type(typeA))
                .field(newFieldDefinition().name("c").type(typeC))
                .build()

        when:
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        then:
        schema.getType("A") != null
        schema.getType("B") != null
        schema.getType("InputTypeX") != null
        schema.getType("InputTypeY") != null
        schema.getType("InputTypeZ") != null
    }

    def "list and non nulls work when direct references are edited"() {

        def typeX = newObject().name("TypeX")
                .field(newFieldDefinition().name("f1").type(GraphQLString))
                .build()
        def queryType = newObject().name("Query")
                .field(newFieldDefinition().name("direct").type(typeX))
                .field(newFieldDefinition().name("indirectNonNull").type(nonNull(typeRef("TypeX"))))
                .field(newFieldDefinition().name("indirectList").type(GraphQLList.list(nonNull(typeRef("TypeX")))))
                .build()

        when:
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        then:
        schema.getType("TypeX") != null

        // now edit away the actual strong reference
        when:
        GraphQLTypeVisitor visitor = new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                if (node.getName() == "direct") {
                    return deleteNode(context)
                }
                return TraversalControl.CONTINUE
            }
        }

        GraphQLSchema transformedSchema = SchemaTransformer.transformSchema(schema, visitor)

        then:
        transformedSchema.getType("TypeX") != null
    }

    def "cheap transform without types transformation works"() {

        def sdl = '''
        "a schema involving pets"
        schema {
            query : Query
        }
        type Query {
            field1 : Dog
            field2 : Cat
        }
        
        type Dog {
            name : String
        }

        type Cat {
            name : String
        }
            
        '''


        when:
        DataFetcher nameDF = { env -> "name" }
        def originalSchema = TestUtil.schema(sdl, ["Dog": ["name": nameDF]])
        def originalCodeRegistry = originalSchema.getCodeRegistry()

        then:
        originalSchema.getDescription() == "a schema involving pets"
        originalSchema.containsType("Dog")
        !originalSchema.containsType("Elephant")

        originalCodeRegistry.getDataFetcher(originalSchema.getObjectType("Dog"), originalSchema.getObjectType("Dog").getField("name")) === nameDF

        when:
        def newRegistry = originalCodeRegistry.transform({ bld -> bld.clearDataFetchers() })
        def newSchema = originalSchema.transformWithoutTypes({
            it.description("A new home for pets").codeRegistry(newRegistry)
        })

        then:

        newSchema.getDescription() == "A new home for pets"
        newSchema.containsType("Dog")
        !newSchema.containsType("Elephant")

        def dogType = newSchema.getObjectType("Dog")
        dogType === originalSchema.getObjectType("Dog") // schema type graph is the same

        newRegistry == newSchema.getCodeRegistry()
        newRegistry != originalCodeRegistry


        def newDF = newRegistry.getDataFetcher(dogType, dogType.getField("name"))
        newDF !== nameDF
        newDF instanceof LightDataFetcher // defaulted in
    }

    def "can get by field co-ordinate"() {
        when:
        def fieldDef = starWarsSchema.getFieldDefinition(FieldCoordinates.coordinates("QueryType", "hero"))

        then:
        fieldDef.name == "hero"
        (fieldDef.type as GraphQLInterfaceType).getName() == "Character"

        when:
        fieldDef = starWarsSchema.getFieldDefinition(FieldCoordinates.coordinates("X", "hero"))

        then:
        fieldDef == null

        when:
        fieldDef = starWarsSchema.getFieldDefinition(FieldCoordinates.coordinates("QueryType", "X"))

        then:
        fieldDef == null

        when:
        starWarsSchema.getFieldDefinition(FieldCoordinates.coordinates("Episode", "JEDI"))

        then:
        thrown(AssertException)

        when:
        fieldDef = starWarsSchema.getFieldDefinition(FieldCoordinates.systemCoordinates("__typename"))

        then:
        fieldDef == starWarsSchema.getIntrospectionTypenameFieldDefinition()

        when:
        fieldDef = starWarsSchema.getFieldDefinition(FieldCoordinates.systemCoordinates("__type"))

        then:
        fieldDef == starWarsSchema.getIntrospectionTypeFieldDefinition()

        when:
        fieldDef = starWarsSchema.getFieldDefinition(FieldCoordinates.systemCoordinates("__schema"))

        then:
        fieldDef == starWarsSchema.getIntrospectionSchemaFieldDefinition()

        when:
        starWarsSchema.getFieldDefinition(FieldCoordinates.systemCoordinates("__junk"))

        then:
        thrown(AssertException)

    }

    def "additionalTypes can contain any type when building programmatically - not restricted to detached types"() {
        given: "types that will be directly reachable from Query"
        def simpleType = newObject()
                .name("SimpleType")
                .field(newFieldDefinition()
                        .name("name")
                        .type(GraphQLString))
                .build()

        def simpleInputType = newInputObject()
                .name("SimpleInput")
                .field(newInputObjectField()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def simpleInterface = GraphQLInterfaceType.newInterface()
                .name("SimpleInterface")
                .field(newFieldDefinition()
                        .name("id")
                        .type(GraphQLString))
                .build()

        def simpleUnion = GraphQLUnionType.newUnionType()
                .name("SimpleUnion")
                .possibleType(simpleType)
                .build()

        def simpleEnum = GraphQLEnumType.newEnum()
                .name("SimpleEnum")
                .value("VALUE_A")
                .value("VALUE_B")
                .build()

        def simpleScalar = GraphQLScalarType.newScalar()
                .name("SimpleScalar")
                .coercing(new Coercing() {
                    @Override
                    Object serialize(Object dataFetcherResult) { return dataFetcherResult }

                    @Override
                    Object parseValue(Object input) { return input }

                    @Override
                    Object parseLiteral(Object input) { return input }
                })
                .build()

        and: "a query type that references all these types directly"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("simpleField")
                        .type(simpleType))
                .field(newFieldDefinition()
                        .name("interfaceField")
                        .type(simpleInterface))
                .field(newFieldDefinition()
                        .name("unionField")
                        .type(simpleUnion))
                .field(newFieldDefinition()
                        .name("enumField")
                        .type(simpleEnum))
                .field(newFieldDefinition()
                        .name("scalarField")
                        .type(simpleScalar))
                .field(newFieldDefinition()
                        .name("inputField")
                        .type(GraphQLString)
                        .argument(newArgument()
                                .name("input")
                                .type(simpleInputType)))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(simpleInterface, { env -> simpleType })
                .typeResolver(simpleUnion, { env -> simpleType })
                .build()

        when: "we add ALL types (including already reachable ones) as additionalTypes"
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry)
                .additionalType(simpleType)        // already reachable via Query.simpleField
                .additionalType(simpleInputType)   // already reachable via Query.inputField argument
                .additionalType(simpleInterface)   // already reachable via Query.interfaceField
                .additionalType(simpleUnion)       // already reachable via Query.unionField
                .additionalType(simpleEnum)        // already reachable via Query.enumField
                .additionalType(simpleScalar)      // already reachable via Query.scalarField
                .build()

        then: "schema builds successfully - no restriction on what can be in additionalTypes"
        schema != null

        and: "all types are in the type map (as expected)"
        schema.getType("SimpleType") == simpleType
        schema.getType("SimpleInput") == simpleInputType
        schema.getType("SimpleInterface") == simpleInterface
        schema.getType("SimpleUnion") == simpleUnion
        schema.getType("SimpleEnum") == simpleEnum
        schema.getType("SimpleScalar") == simpleScalar

        and: "additionalTypes contains all types we added - even though they were already reachable"
        schema.getAdditionalTypes().size() == 6
        schema.getAdditionalTypes().contains(simpleType)
        schema.getAdditionalTypes().contains(simpleInputType)
        schema.getAdditionalTypes().contains(simpleInterface)
        schema.getAdditionalTypes().contains(simpleUnion)
        schema.getAdditionalTypes().contains(simpleEnum)
        schema.getAdditionalTypes().contains(simpleScalar)
    }

}
