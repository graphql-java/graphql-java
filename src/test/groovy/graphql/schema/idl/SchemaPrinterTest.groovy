package graphql.schema.idl

import graphql.AssertException
import graphql.GraphQL
import graphql.TestUtil
import graphql.TypeResolutionEnvironment
import graphql.introspection.IntrospectionQuery
import graphql.introspection.IntrospectionResultToSchema
import graphql.schema.Coercing
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.GraphQLUnionType
import graphql.schema.TypeResolver
import spock.lang.Specification

import java.util.function.UnaryOperator

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.byGreatestLength
import static graphql.TestUtil.mockArguments
import static graphql.TestUtil.mockDirectivesWithArguments
import static graphql.TestUtil.mockScalar
import static graphql.TestUtil.mockTypeRuntimeWiring
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLUnionType.newUnionType
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.SchemaPrinter.Options.defaultOptions

class SchemaPrinterTest extends Specification {

    GraphQLSchema starWarsSchema() {
        def wiring = newRuntimeWiring()
                .type("Character", { type -> type.typeResolver(resolver) } as UnaryOperator<TypeRuntimeWiring.Builder>)
                .type("Node", { type -> type.typeResolver(resolver) } as UnaryOperator<TypeRuntimeWiring.Builder>)
                .scalar(ASTEROID)
                .build()
        GraphQLSchema schema = TestUtil.schemaFromResource("starWarsSchemaExtended.graphqls", wiring)
        schema
    }


    GraphQLScalarType ASTEROID = new GraphQLScalarType("Asteroid", "desc", new Coercing() {
        @Override
        Object serialize(Object input) {
            throw new UnsupportedOperationException("Not implemented")
        }

        @Override
        Object parseValue(Object input) {
            throw new UnsupportedOperationException("Not implemented")
        }

        @Override
        Object parseLiteral(Object input) {
            throw new UnsupportedOperationException("Not implemented")
        }
    })

    def resolver = new TypeResolver() {

        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    static class MyGraphQLObjectType extends GraphQLObjectType {

        MyGraphQLObjectType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions) {
            super(name, description, fieldDefinitions, new ArrayList<GraphQLOutputType>())
        }
    }

    def "typeString"() {

        GraphQLType type1 = nonNull(list(nonNull(list(nonNull(GraphQLInt)))))

        def typeStr1 = new SchemaPrinter().typeString(type1)

        expect:
        typeStr1 == "[[Int!]!]!"
    }

    def "argsString"() {
        def argument1 = new GraphQLArgument("arg1", null, list(nonNull(GraphQLInt)), 10)
        def argument2 = new GraphQLArgument("arg2", null, GraphQLString, null)
        def argument3 = new GraphQLArgument("arg3", null, GraphQLString, "default")
        def argStr = new SchemaPrinter().argsString([argument1, argument2, argument3])

        expect:

        argStr == "(arg1: [Int!] = 10, arg2: String, arg3: String = \"default\")"
    }

    def "argsString_sorts"() {
        def argument1 = new GraphQLArgument("arg1", null, list(nonNull(GraphQLInt)), 10)
        def argument2 = new GraphQLArgument("arg2", null, GraphQLString, null)
        def argument3 = new GraphQLArgument("arg3", null, GraphQLString, "default")
        def argStr = new SchemaPrinter().argsString([argument2, argument1, argument3])

        expect:

        argStr == "(arg1: [Int!] = 10, arg2: String, arg3: String = \"default\")"
    }

    def "print type direct"() {
        GraphQLSchema schema = starWarsSchema()

        def result = new SchemaPrinter().print(schema.getType("Character"))

        expect:
        result ==
                """interface Character {
  appearsIn: [Episode]!
  friends: [Character]
  id: ID!
  name: String!
}

"""
    }

    def "starWars default Test"() {
        GraphQLSchema schema = starWarsSchema()

        def result = new SchemaPrinter().print(schema)

        expect:
        result != null
        !result.contains("scalar")
        !result.contains("__TypeKind")
    }

    def "starWars non default Test"() {
        GraphQLSchema schema = starWarsSchema()

        def options = defaultOptions()
                .includeIntrospectionTypes(true)
                .includeScalarTypes(true)

        def result = new SchemaPrinter(options).print(schema)

        expect:
        result != null
        result.contains("scalar")
        result.contains("__TypeKind")
    }

    def "default root names are handled"() {
        def schema = TestUtil.schema("""
            type Query {
                field: String
            }

            type Mutation {
                field: String
            }

            type Subscription {
                field: String
            }
        """)


        def result = new SchemaPrinter().print(schema)

        expect:
        result == """type Mutation {
  field: String
}

type Query {
  field: String
}

type Subscription {
  field: String
}
"""
    }

    def "schema prints if forced with default root names"() {
        def schema = TestUtil.schema("""
            type Query {
                field: String
            }

            type Mutation {
                field: String
            }

            type Subscription {
                field: String
            }
        """)

        def options = defaultOptions()
                .includeSchemaDefintion(true)

        def result = new SchemaPrinter(options).print(schema)

        expect:
        result == """schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}

type Mutation {
  field: String
}

type Query {
  field: String
}

type Subscription {
  field: String
}
"""
    }


    def "schema is printed if default root names are not ALL present"() {
        def schema = TestUtil.schema("""
            type Query {
                field: String
            }

            type MutationX {
                field: String
            }

            type Subscription {
                field: String
            }
            
            schema {
                query: Query
                mutation: MutationX
                subscription: Subscription
            } 
            
        """)


        def result = new SchemaPrinter().print(schema)

        expect:
        result == """schema {
  query: Query
  mutation: MutationX
  subscription: Subscription
}

type MutationX {
  field: String
}

type Query {
  field: String
}

type Subscription {
  field: String
}
"""
    }

    def "prints object description as comment"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").type(GraphQLString).build()
        def queryType = GraphQLObjectType.newObject().name("Query").description("About Query\nSecond Line").field(fieldDefinition).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """#About Query
#Second Line
type Query {
  field: String
}
"""
    }

    def "prints field description as comment"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").description("About field\nsecond").type(GraphQLString).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """type Query {
  #About field
  #second
  field: String
}
"""
    }

    def "does not print empty field description as comment"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").description("").type(GraphQLString).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """type Query {
  field: String
}
"""
    }

    def "prints enum description as comment"() {
        given:
        GraphQLEnumType graphQLEnumType = GraphQLEnumType.newEnum()
                .name("Enum")
                .description("About enum")
                .value("value", "value", "value desc")
                .build()
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").type(graphQLEnumType).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """type Query {
  field: Enum
}

#About enum
enum Enum {
  #value desc
  value
}
"""

    }

    def "prints union description as comment"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").type(GraphQLString).build()
        def possibleType = GraphQLObjectType.newObject().name("PossibleType").field(fieldDefinition).build()
        GraphQLUnionType unionType = GraphQLUnionType.newUnionType()
                .name("Union")
                .description("About union")
                .possibleType(possibleType)
                .typeResolver({ it -> null })
                .build()
        GraphQLFieldDefinition fieldDefinition2 = newFieldDefinition()
                .name("field").type(unionType).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition2).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """#About union
union Union = PossibleType

type PossibleType {
  field: String
}

type Query {
  field: Union
}
"""

    }

    def "prints union"() {
        def possibleType1 = GraphQLObjectType.newObject().name("PossibleType1").field(
                newFieldDefinition().name("field").type(GraphQLString).build()
        ).build()
        def possibleType2 = GraphQLObjectType.newObject().name("PossibleType2").field(
                newFieldDefinition().name("field").type(GraphQLString).build()
        ).build()
        GraphQLUnionType unionType = GraphQLUnionType.newUnionType()
                .name("Union")
                .possibleType(possibleType1)
                .possibleType(possibleType2)
                .typeResolver({ it -> null })
                .build()
        GraphQLFieldDefinition fieldDefinition2 = newFieldDefinition()
                .name("field").type(unionType).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition2).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """union Union = PossibleType1 | PossibleType2

type PossibleType1 {
  field: String
}

type PossibleType2 {
  field: String
}

type Query {
  field: Union
}
"""

    }

    def "prints input description as comment"() {
        given:
        GraphQLInputType inputType = GraphQLInputObjectType.newInputObject()
                .name("Input")
                .field(newInputObjectField().name("field").description("about field").type(GraphQLString).build())
                .description("About input")
                .build()
        GraphQLFieldDefinition fieldDefinition2 = newFieldDefinition()
                .name("field")
                .argument(newArgument().name("arg").type(inputType).build())
                .type(GraphQLString).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition2).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """type Query {
  field(arg: Input): String
}

#About input
input Input {
  #about field
  field: String
}
"""

    }

    def "prints interface description as comment"() {
        given:
        GraphQLInterfaceType graphQLInterfaceType = newInterface()
                .name("Interface")
                .description("about interface")
                .field(newFieldDefinition().name("field").description("about field").type(GraphQLString).build())
                .typeResolver({ it -> null })
                .build()
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").type(graphQLInterfaceType).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """#about interface
interface Interface {
  #about field
  field: String
}

type Query {
  field: Interface
}
"""
    }

    def "prints scalar description as comment"() {
        given:
        GraphQLScalarType myScalar = new GraphQLScalarType("Scalar", "about scalar", new Coercing() {
            @Override
            Object serialize(Object input) {
                return null
            }

            @Override
            Object parseValue(Object input) {
                return null
            }

            @Override
            Object parseLiteral(Object input) {
                return null
            }
        })
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").type(myScalar).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter(defaultOptions().includeScalarTypes(true)).print(schema)

        then:
        result == """type Query {
  field: Scalar
}

#about scalar
scalar Scalar
"""
    }

    def "special formatting for argument descriptions"() {
        GraphQLFieldDefinition fieldDefinition2 = newFieldDefinition()
                .name("field")
                .argument(newArgument().name("arg1").description("about arg1").type(GraphQLString).build())
                .argument(newArgument().name("arg2").type(GraphQLString).build())
                .argument(newArgument().name("arg3").description("about 3\nsecond line").type(GraphQLString).build())
                .type(GraphQLString).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition2).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """type Query {
  field(
    #about arg1
    arg1: String, 
    arg2: String, 
    \"\"\"
    about 3
    second line
    \"\"\"
    arg3: String
  ): String
}
"""

    }

    def "prints derived object type"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition().name("field").type(GraphQLString).build()
        def queryType = new MyGraphQLObjectType("Query", "About Query\nSecond Line", Arrays.asList(fieldDefinition))
        def schema = GraphQLSchema.newSchema().query(queryType).build()

        when:
        def result = new SchemaPrinter().print(schema)

        then:
        result == """#About Query
#Second Line
type Query {
  field: String
}
"""
    }

    def "prints extended types"() {
        given:
        def idl = '''
            type Query {
                field : CustomScalar
                bigDecimal : BigDecimal
            }
            
            scalar CustomScalar
        '''

        def schema = TestUtil.schema(idl, newRuntimeWiring().scalar(mockScalar("CustomScalar")))


        when:
        def result = new SchemaPrinter(options).print(schema)

        then:

        if (expectedStrs.isEmpty()) {
            assert !result.contains("scalar")
        } else {
            expectedStrs.forEach({ s -> assert result.contains(s) })
        }


        where:
        expectedStrs                                 | options
        []                                           | defaultOptions()
        ["scalar CustomScalar"]                      | defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(false)
        ["scalar BigDecimal", "scalar CustomScalar"] | defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true)
        ["scalar CustomScalar"]                      | defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(false)
    }


    def "schema will be sorted"() {
        def schema = TestUtil.schema("""
            type Query {
                fieldB(argZ : String, argY : Int, argX : String) : String
                fieldA(argZ : String, argY : Int, argX : String) : String
                fieldC(argZ : String, argY : Int, argX : String) : String
                fieldE : TypeE
                fieldD : TypeD
            }
            
            type TypeE {
                fieldA : String
                fieldC : String
                fieldB : String
            }

            type TypeD {
                fieldB : String
                fieldA : String
                fieldC : String
            }
        """)


        def result = new SchemaPrinter().print(schema)

        expect:
        result == """type Query {
  fieldA(argX: String, argY: Int, argZ: String): String
  fieldB(argX: String, argY: Int, argZ: String): String
  fieldC(argX: String, argY: Int, argZ: String): String
  fieldD: TypeD
  fieldE: TypeE
}

type TypeD {
  fieldA: String
  fieldB: String
  fieldC: String
}

type TypeE {
  fieldA: String
  fieldB: String
  fieldC: String
}
"""
    }


    def "print introspection result back to IDL"() {
        GraphQLSchema schema = starWarsSchema()
        def graphQL = GraphQL.newGraphQL(schema).build()

        def executionResult = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)

        def schemaDefinition = new IntrospectionResultToSchema().createSchemaDefinition(executionResult)

        def result = new SchemaPrinter().print(schemaDefinition)

        expect:
        result ==
                """interface Character {
  appearsIn: [Episode]!
  friends: [Character]
  id: ID!
  name: String!
}

interface Node {
  id: ID!
}

type Droid implements Character & Node {
  appearsIn: [Episode]!
  friends: [Character]
  id: ID!
  madeOn: Planet
  name: String!
  primaryFunction: String
}

type Human implements Character & Node {
  appearsIn: [Episode]!
  friends: [Character]
  homePlanet: String
  id: ID!
  name: String!
}

type Planet {
  hitBy: Asteroid
  name: String
}

type Query {
  droid(id: ID!): Droid
  hero(episode: Episode): Character
  node(id: ID!): Node
}

type Starship implements Node {
  id: ID!
  name: String
}

enum Episode {
  EMPIRE
  JEDI
  NEWHOPE
}
"""
    }


    def "AST doc string entries are printed if present"() {
        def schema = TestUtil.schema('''
            # comments up here
            """docstring"""
            # and comments as well down here
            type Query {
                "field single desc"
                field: String
            }
        ''')


        def result = new SchemaPrinter().print(schema)

        expect:
        result == '''"""docstring"""
type Query {
  "field single desc"
  field: String
}
'''
    }


    def idlWithDirectives() {
        return """
            
            interface SomeInterface @interfaceTypeDirective {
                fieldA : String @interfaceFieldDirective
            }
            
            union SomeUnion @unionTypeDirective = Single | SomeImplementingType
            
            type Query @query1 @query2(arg1:"x") {
                fieldA : String @fieldDirective1 @fieldDirective2(argStr:"str", argInt : 1, argFloat : 1.0, argBool : false)
                fieldB(input : SomeInput) : SomeScalar
                fieldC : SomeEnum
                fieldD : SomeInterface
                fieldE : SomeUnion
            }
            
            type Single @single {
                fieldA : String @singleField
            }
            
            type SomeImplementingType implements SomeInterface @interfaceImplementingTypeDirective {
                fieldA : String @interfaceImplementingFieldDirective
            }
            
            enum SomeEnum @enumTypeDirective {
                SOME_ENUM_VALUE @enumValueDirective
            }
            
            scalar SomeScalar @scalarDirective
            
            input SomeInput @inputTypeDirective {
                fieldA : String @inputFieldDirective
            }
        """
    }


    def "directives will be printed with the includeDirectives flag set"() {
        given:
        def registry = new SchemaParser().parse(idlWithDirectives())
        def runtimeWiring = newRuntimeWiring()
                .scalar(mockScalar(registry.scalars().get("SomeScalar")))
                .type(mockTypeRuntimeWiring("SomeInterface", true))
                .type(mockTypeRuntimeWiring("SomeUnion", true))
                .build()
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(false)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(defaultOptions().includeScalarTypes(true)).print(schema)

        then:
        // args and directives are sorted like the rest of the schema printer
        result == '''interface SomeInterface @interfaceTypeDirective {
  fieldA: String @interfaceFieldDirective
}

union SomeUnion @unionTypeDirective = Single | SomeImplementingType

type Query @query1 @query2(arg1 : "x") {
  fieldA: String @fieldDirective1 @fieldDirective2(argBool : false, argFloat : 1.0, argInt : 1, argStr : "str")
  fieldB(input: SomeInput): SomeScalar
  fieldC: SomeEnum
  fieldD: SomeInterface
  fieldE: SomeUnion
}

type Single @single {
  fieldA: String @singleField
}

type SomeImplementingType implements SomeInterface @interfaceImplementingTypeDirective {
  fieldA: String @interfaceImplementingFieldDirective
}

enum SomeEnum @enumTypeDirective {
  SOME_ENUM_VALUE @enumValueDirective
}

scalar SomeScalar @scalarDirective

input SomeInput @inputTypeDirective {
  fieldA: String @inputFieldDirective
}
'''
        when:
        def resultNoDirectives = new SchemaPrinter(defaultOptions()
                .includeScalarTypes(true)
                .includeDirectives(false))
                .print(schema)

        then:
        // args and directives are sorted like the rest of the schema printer
        resultNoDirectives == '''interface SomeInterface {
  fieldA: String
}

union SomeUnion = Single | SomeImplementingType

type Query {
  fieldA: String
  fieldB(input: SomeInput): SomeScalar
  fieldC: SomeEnum
  fieldD: SomeInterface
  fieldE: SomeUnion
}

type Single {
  fieldA: String
}

type SomeImplementingType implements SomeInterface {
  fieldA: String
}

enum SomeEnum {
  SOME_ENUM_VALUE
}

scalar SomeScalar

input SomeInput {
  fieldA: String
}
'''
    }


    def "directives with default values are printed correctly"() {
        given:
        def idl = """

            type Field {
              active : Enum
              deprecated : Enum @deprecated
              deprecatedWithReason : Enum @deprecated(reason : "Custom reason 1")
            }
            
            type Query {
                field : Field
            }
            
            enum Enum {
              ACTIVE
              DEPRECATED @deprecated
              DEPRECATED_WITH_REASON @deprecated(reason : "Custom reason 2")
            }
        """
        def registry = new SchemaParser().parse(idl)
        def runtimeWiring = newRuntimeWiring().build()
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(false)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(defaultOptions().includeScalarTypes(true)).print(schema)

        then:
        // args and directives are sorted like the rest of the schema printer
        result == '''type Field {
  active: Enum
  deprecated: Enum @deprecated(reason : "No longer supported")
  deprecatedWithReason: Enum @deprecated(reason : "Custom reason 1")
}

type Query {
  field: Field
}

enum Enum {
  ACTIVE
  DEPRECATED @deprecated(reason : "No longer supported")
  DEPRECATED_WITH_REASON @deprecated(reason : "Custom reason 2")
}
'''
    }

    def "scalarPrinter default comparator"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def options = defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true)
        def result = new SchemaPrinter(options).print(scalarType)

        then:
        result == '''#TestScalar
scalar TestScalar @a(a, bb) @bb(a, bb)

'''
    }

    def "enumPrinter default comparator"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(enumType)

        then:
        result == '''enum TestEnum @a(a, bb) @bb(a, bb) {
  a @a(a, bb) @bb(a, bb)
  bb @a(a, bb) @bb(a, bb)
}

'''
    }

    def "unionPrinter default comparator"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a").build())
                .possibleType(newObject().name("bb").build())
                .build()

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(unionType)

        then:
        result == '''union TestUnion @a(a, bb) @bb(a, bb) = a | bb

'''
    }

    def "interfacePrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(interfaceType)

        then:
        result == '''interface TypeA @a(a, bb) @bb(a, bb) {
  a(a: Int, bb: Int): String @a(a, bb) @bb(a, bb)
  bb(a: Int, bb: Int): String @a(a, bb) @bb(a, bb)
}

'''
    }

    def "objectPrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(newInterface().name("a").build(), newInterface().name("bb").build())
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(objectType)

        then:
        result == '''type TypeA implements a & bb @a(a, bb) @bb(a, bb) {
  a(a: Int, bb: Int): String @a(a, bb) @bb(a, bb)
  bb(a: Int, bb: Int): String @a(a, bb) @bb(a, bb)
}

'''
    }

    def "inputObjectPrinter default comparator"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).print(inputObjectType)

        then:
        result == '''input TypeA @a(a, bb) @bb(a, bb) {
  a: String @a(a, bb) @bb(a, bb)
  bb: String @a(a, bb) @bb(a, bb)
}

'''
    }

    def "argsString default comparator"() {
        given:
        def args = mockArguments("a", "bb")

        when:
        def options = defaultOptions()
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(args) == '''(a: Int, bb: Int)'''
        printer.argsString(null, args) == '''(a: Int, bb: Int)'''
    }

    def "directivesString default comparator"() {
        given:
        def directives = mockDirectivesWithArguments("a", "bb").collect { it }

        when:
        def options = defaultOptions()
        def result = new SchemaPrinter(options).directivesString(null, directives)

        then:
        result == ''' @a(a, bb) @bb(a, bb)'''
    }


    def "SchemaPrinterComparatorEnvironment valid instance"() {
        when:
        environment.build()

        then:
        notThrown()

        where:

        environment << [
                SchemaPrinterComparatorEnvironment.newEnvironment()
                        .withParentType(GraphQLObjectType.class)
                        .withElementType(GraphQLFieldDefinition.class),
                SchemaPrinterComparatorEnvironment.newEnvironment()
                        .withElementType(GraphQLFieldDefinition.class)
        ]
    }

    def "SchemaPrinterComparatorEnvironment invalid instance with missing elementType"() {
        when:
        environment.build()

        then:
        thrown(AssertException)

        where:

        environment << [
                SchemaPrinterComparatorEnvironment.newEnvironment().withParentType(GraphQLObjectType.class),
                SchemaPrinterComparatorEnvironment.newEnvironment()
        ]
    }

    def "scalarPrinter uses most specific registered comparators"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withParentType(GraphQLScalarType.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLDirective.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true).setComparators(registry)
        def result = new SchemaPrinter(options).print(scalarType)

        then:
        result == '''#TestScalar
scalar TestScalar @bb(bb, a) @a(bb, a)

'''
    }

    def "scalarPrinter uses least specific registered comparators"() {
        given:
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true).setComparators(registry)
        def result = new SchemaPrinter(options).print(scalarType)

        then:
        result == '''#TestScalar
scalar TestScalar @bb(bb, a) @a(bb, a)

'''
    }

    def "enumPrinter uses most specific registered comparators"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withParentType(GraphQLEnumType.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLEnumType.class).withElementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, byGreatestLength)
                .add({ it.withParentType(GraphQLEnumValueDefinition.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLDirective.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(enumType)

        then:
        result == '''enum TestEnum @bb(bb, a) @a(bb, a) {
  bb @bb(bb, a) @a(bb, a)
  a @bb(bb, a) @a(bb, a)
}

'''
    }

    def "enumPrinter uses least specific registered comparators"() {
        given:
        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(1).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withElementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, byGreatestLength)
                .add({ it.withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(enumType)

        then:
        result == '''enum TestEnum @bb(bb, a) @a(bb, a) {
  bb @bb(bb, a) @a(bb, a)
  a @bb(bb, a) @a(bb, a)
}

'''
    }


    def "unionPrinter uses most specific registered comparators"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a").build())
                .possibleType(newObject().name("bb").build())
                .build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withParentType(GraphQLUnionType.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLUnionType.class).withElementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .add({ it.withParentType(GraphQLDirective.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(unionType)

        then:
        result == '''union TestUnion @bb(bb, a) @a(bb, a) = bb | a

'''
    }


    def "unionPrinter uses least specific registered comparators"() {
        given:
        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a").build())
                .possibleType(newObject().name("bb").build())
                .build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withElementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .add({ it.withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(unionType)

        then:
        result == '''union TestUnion @bb(bb, a) @a(bb, a) = bb | a

'''
    }


    def "interfacePrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withParentType(GraphQLInterfaceType.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLInterfaceType.class).withElementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .add({ it.withParentType(GraphQLFieldDefinition.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .add({ it.withParentType(GraphQLFieldDefinition.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLDirective.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(interfaceType)

        then:
        result == '''interface TypeA @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "interfacePrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInterfaceType interfaceType = newInterface().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withElementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .add({ it.withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()

        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(interfaceType)

        then:
        result == '''interface TypeA @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "objectPrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(newInterface().name("a") .build(), newInterface().name("bb").build())
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withParentType(GraphQLObjectType.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLObjectType.class).withElementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .add({ it.withParentType(GraphQLObjectType.class).withElementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .add({ it.withParentType(GraphQLFieldDefinition.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .add({ it.withParentType(GraphQLFieldDefinition.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLDirective.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(objectType)

        then:
        result == '''type TypeA implements bb & a @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "objectPrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLObjectType objectType = newObject().name("TypeA")
                .withInterfaces(newInterface().name("a") .build(), newInterface().name("bb").build())
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withElementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .add({ it.withElementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .add({ it.withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(objectType)

        then:
        result == '''type TypeA implements bb & a @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "inputObjectPrinter uses most specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withParentType(GraphQLInputObjectType.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLInputObjectType.class).withElementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, byGreatestLength)
                .add({ it.withParentType(GraphQLInputObjectField.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLDirective.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(inputObjectType)

        then:
        result == '''input TypeA @bb(bb, a) @a(bb, a) {
  bb: String @bb(bb, a) @a(bb, a)
  a: String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "inputObjectPrinter uses least specific registered comparators"() {
        given:
        // @formatter:off
        GraphQLInputObjectType inputObjectType = newInputObject().name("TypeA")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withElementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, byGreatestLength)
                .add({ it.withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).print(inputObjectType)

        then:
        result == '''input TypeA @bb(bb, a) @a(bb, a) {
  bb: String @bb(bb, a) @a(bb, a)
  a: String @bb(bb, a) @a(bb, a)
}

'''
    }

    def "argsString uses most specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field").type(GraphQLInt).argument(mockArguments("a", "bb")).build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withParentType(GraphQLFieldDefinition.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(GraphQLFieldDefinition.class, field.arguments) == '''(bb: Int, a: Int)'''
    }

    def "argsString uses least specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field").type(GraphQLInt).argument(mockArguments("a", "bb")).build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def printer = new SchemaPrinter(options)

        then:
        printer.argsString(GraphQLFieldDefinition.class, field.arguments) == '''(bb: Int, a: Int)'''
        printer.argsString(null, field.arguments) == '''(bb: Int, a: Int)'''
    }


    def "directivesString uses most specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field")
                .type(GraphQLString)
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withParentType(GraphQLFieldDefinition.class).withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLDirective.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).directivesString(GraphQLFieldDefinition.class, field.directives)

        then:
        result == ''' @bb(bb, a) @a(bb, a)'''
    }

    def "directivesString uses least specific registered comparators"() {
        given:
        def field = newFieldDefinition().name("field")
                .type(GraphQLString)
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withParentType(GraphQLDirective.class).withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().setComparators(registry)
        def result = new SchemaPrinter(options).directivesString(GraphQLFieldDefinition.class, field.directives)

        then:
        result == ''' @bb(bb, a) @a(bb, a)'''
    }


    def "least specific comparator applied across different types"() {
        given:
        // @formatter:off
        GraphQLScalarType scalarType = newScalar(mockScalar("TestScalar"))
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .build()

        GraphQLUnionType unionType = newUnionType().name("TestUnion")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .possibleType(newObject().name("a").build())
                .possibleType(newObject().name("bb").build())
                .build()

        GraphQLEnumType enumType = newEnum().name("TestEnum")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .value(newEnumValueDefinition().name("a").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .value(newEnumValueDefinition().name("bb").value(0).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLObjectType objectType = newObject().name("TestObjectType")
                .withInterfaces(newInterface().name("a") .build(), newInterface().name("bb").build())
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString).withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLInterfaceType interfaceType = newInterface().name("TestInterfaceType")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newFieldDefinition().name("a")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newFieldDefinition().name("bb")
                    .argument(mockArguments("a", "bb"))
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()

        GraphQLInputObjectType inputObjectType = newInputObject().name("TestInputObjectType")
                .withDirectives(mockDirectivesWithArguments("a", "bb"))
                .field(newInputObjectField().name("a")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .field(newInputObjectField().name("bb")
                    .type(GraphQLString)
                    .withDirectives(mockDirectivesWithArguments("a", "bb")).build())
                .build()
        // @formatter:on

        when:
        def registry = DefaultSchemaPrinterComparatorRegistry.newComparators()
                .add({ it.withElementType(GraphQLFieldDefinition.class) }, GraphQLFieldDefinition.class, byGreatestLength)
                .add({ it.withElementType(GraphQLInputObjectField.class) }, GraphQLInputObjectField.class, byGreatestLength)
                .add({ it.withElementType(GraphQLEnumValueDefinition.class) }, GraphQLEnumValueDefinition.class, byGreatestLength)
                .add({ it.withElementType(GraphQLOutputType.class) }, GraphQLOutputType.class, byGreatestLength)
                .add({ it.withElementType(GraphQLDirective.class) }, GraphQLDirective.class, byGreatestLength)
                .add({ it.withElementType(GraphQLArgument.class) }, GraphQLArgument.class, byGreatestLength)
                .build()
        def options = defaultOptions().includeScalarTypes(true).includeExtendedScalarTypes(true).setComparators(registry)
        def printer = new SchemaPrinter(options)

        def scalarResult = printer.print(scalarType)
        def enumResult = printer.print(enumType)
        def unionResult = printer.print(unionType)
        def objectTypeResult = printer.print(objectType)
        def interfaceTypeResult = printer.print(interfaceType)
        def inputObjectTypeResult = printer.print(inputObjectType)

        then:

        scalarResult == '''#TestScalar
scalar TestScalar @bb(bb, a) @a(bb, a)

'''

        enumResult == '''enum TestEnum @bb(bb, a) @a(bb, a) {
  bb @bb(bb, a) @a(bb, a)
  a @bb(bb, a) @a(bb, a)
}

'''

        unionResult == '''union TestUnion @bb(bb, a) @a(bb, a) = bb | a

'''

        interfaceTypeResult == '''interface TestInterfaceType @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''

        objectTypeResult == '''type TestObjectType implements bb & a @bb(bb, a) @a(bb, a) {
  bb(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
  a(bb: Int, a: Int): String @bb(bb, a) @a(bb, a)
}

'''

        inputObjectTypeResult == '''input TestInputObjectType @bb(bb, a) @a(bb, a) {
  bb: String @bb(bb, a) @a(bb, a)
  a: String @bb(bb, a) @a(bb, a)
}

'''
    }
}