package graphql.schema

import graphql.AssertException
import graphql.Directives
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import spock.lang.Specification

import java.util.function.UnaryOperator

import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.characterInterface
import static graphql.StarWarsSchema.droidType
import static graphql.StarWarsSchema.humanType
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

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
        GraphQLSchema.newSchema()
                .query(newObject()
                        .name("QueryType")
                        .field(newFieldDefinition()
                                .name("hero")
                                .type(GraphQLString)
                                .dataFetcher({ env -> null })))
    }

    def additionalType1 = newObject()
            .name("Additional1")
            .field(newFieldDefinition()
                    .name("field")
                    .type(GraphQLString)
                    .dataFetcher({ env -> null }))
            .build()

    def additionalType2 = newObject()
            .name("Additional2")
            .field(newFieldDefinition()
                    .name("field")
                    .type(GraphQLString)
                    .dataFetcher({ env -> null }))
            .build()

    def "clear directives works as expected"() {
        setup:
        def schemaBuilder = basicSchemaBuilder()

        when: "no additional directives have been specified"
        def schema = schemaBuilder.build()
        then:
        schema.directives.size() == 4

        when: "clear directives is called"
        schema = schemaBuilder.clearDirectives().build()
        then:
        schema.directives.size() == 2 // @deprecated and @specifiedBy is ALWAYS added if missing

        when: "clear directives is called with more directives"
        schema = schemaBuilder.clearDirectives().additionalDirective(Directives.SkipDirective).build()
        then:
        schema.directives.size() == 3

        when: "the schema is transformed, things are copied"
        schema = schema.transform({ builder -> builder.additionalDirective(Directives.IncludeDirective) })
        then:
        schema.directives.size() == 4
    }

    def "clear additional types  works as expected"() {
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
        schema = schemaBuilder.clearAdditionalTypes().additionalType(additionalType1).build()
        then:
        schema.additionalTypes.size() == 1

        when: "the schema is transformed, things are copied"
        schema = schema.transform({ builder -> builder.additionalType(additionalType2) })
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
        newDF instanceof PropertyDataFetcher // defaulted in
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
}
