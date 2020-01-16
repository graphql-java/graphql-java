package graphql.schema.idl

import graphql.GraphQL
import graphql.Scalars
import graphql.TestUtil
import graphql.TypeResolutionEnvironment
import graphql.introspection.IntrospectionQuery
import graphql.introspection.IntrospectionResultToSchema
import graphql.schema.Coercing
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
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

import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mockScalar
import static graphql.TestUtil.mockTypeRuntimeWiring
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.SchemaPrinter.Options.defaultOptions

class SchemaPrinterTest extends Specification {

    def noDirectivesOption = defaultOptions().includeDirectives(false)

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

    static class MyTestGraphQLObjectType extends MyGraphQLObjectType {

        MyTestGraphQLObjectType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions) {
            super(name, description, fieldDefinitions)
        }
    }


    def "typeString"() {

        GraphQLType type1 = nonNull(list(nonNull(list(nonNull(Scalars.GraphQLInt)))))

        def typeStr1 = new SchemaPrinter().typeString(type1)

        expect:
        typeStr1 == "[[Int!]!]!"
    }

    def "argsString"() {
        def argument1 = new GraphQLArgument("arg1", null, list(nonNull(Scalars.GraphQLInt)), 10)
        def argument2 = new GraphQLArgument("arg2", null, GraphQLString, null)
        def argument3 = new GraphQLArgument("arg3", null, GraphQLString, "default")
        def argStr = new SchemaPrinter().argsString([argument1, argument2, argument3])

        expect:

        argStr == "(arg1: [Int!] = 10, arg2: String, arg3: String = \"default\")"
    }

    def "argsString_sorts"() {
        def argument1 = new GraphQLArgument("arg1", null, list(nonNull(Scalars.GraphQLInt)), 10)
        def argument2 = new GraphQLArgument("arg2", null, GraphQLString, null)
        def argument3 = new GraphQLArgument("arg3", null, GraphQLString, "default")
        def argStr = new SchemaPrinter().argsString([argument2, argument1, argument3])

        expect:

        argStr == "(arg1: [Int!] = 10, arg2: String, arg3: String = \"default\")"
    }

    def "argsString_comments"() {
        def argument1 = new GraphQLArgument("arg1", "A multiline\ncomment", list(nonNull(Scalars.GraphQLInt)), 10)
        def argument2 = new GraphQLArgument("arg2", "A single line comment", list(nonNull(Scalars.GraphQLInt)), 10)
        def argStr = new SchemaPrinter().argsString([argument1, argument2])

        expect:

        argStr == '''(
    """
    A multiline
    comment
    """
    arg1: [Int!] = 10, 
    "A single line comment"
    arg2: [Int!] = 10
  )'''
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


        def result = new SchemaPrinter(noDirectivesOption).print(schema)

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
                .includeDirectives(false)
                .includeSchemaDefinition(true)

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


        def result = new SchemaPrinter(noDirectivesOption).print(schema)

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
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''"""
About Query
Second Line
"""
type Query {
  field: String
}
'''
    }

    def "prints field description as comment"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").description("About field\nsecond").type(GraphQLString).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''type Query {
  """
  About field
  second
  """
  field: String
}
'''
    }

    def "does not print empty field description as comment"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").description("").type(GraphQLString).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(fieldDefinition).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

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
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''type Query {
  field: Enum
}

"About enum"
enum Enum {
  "value desc"
  value
}
'''

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
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''"About union"
union Union = PossibleType

type PossibleType {
  field: String
}

type Query {
  field: Union
}
'''

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
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

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
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''type Query {
  field(arg: Input): String
}

"About input"
input Input {
  "about field"
  field: String
}
'''

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
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''"about interface"
interface Interface {
  "about field"
  field: String
}

type Query {
  field: Interface
}
'''
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
        def result = new SchemaPrinter(defaultOptions().includeScalarTypes(true).includeDirectives(false)).print(schema)

        then:
        result == '''type Query {
  field: Scalar
}

"about scalar"
scalar Scalar
'''
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
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''type Query {
  field(
    "about arg1"
    arg1: String, 
    arg2: String, 
    """
    about 3
    second line
    """
    arg3: String
  ): String
}
'''

    }

    def "prints derived object type"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition().name("field").type(GraphQLString).build()
        def queryType = new MyGraphQLObjectType("Query", "About Query\nSecond Line", Arrays.asList(fieldDefinition))
        def schema = GraphQLSchema.newSchema().query(queryType).build()

        when:
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''"""
About Query
Second Line
"""
type Query {
  field: String
}
'''
    }

    def "concurrentModificationException should not occur when multiple extended graphQL types are used"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition().name("field").type(GraphQLString).build()
        def queryType = new MyTestGraphQLObjectType("Query", "test", Arrays.asList(fieldDefinition))
        def schema = GraphQLSchema.newSchema().query(queryType).build()

        when:
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''"test"
type Query {
  field: String
}
'''
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


        def result = new SchemaPrinter(noDirectivesOption).print(schema)

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

        def result = new SchemaPrinter(noDirectivesOption).print(schemaDefinition)

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
            """
                doc
                 string"""
            # and comments as well down here
            type Query {
                "field single desc"
                field: String
            }
        ''')


        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        expect:
        result == '''"""
doc
 string
"""
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
                fieldF(argWithDirective: String @argDirective): String
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
        result == '''"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Marks the field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ENUM_VALUE

interface SomeInterface @interfaceTypeDirective {
  fieldA: String @interfaceFieldDirective
}

union SomeUnion @unionTypeDirective = Single | SomeImplementingType

type Query @query1 @query2(arg1 : "x") {
  fieldA: String @fieldDirective1 @fieldDirective2(argBool : false, argFloat : 1.0, argInt : 1, argStr : "str")
  fieldB(input: SomeInput): SomeScalar
  fieldC: SomeEnum
  fieldD: SomeInterface
  fieldE: SomeUnion
  fieldF(argWithDirective: String @argDirective): String
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
  fieldF(argWithDirective: String): String
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
        result == '''"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Marks the field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ENUM_VALUE

type Field {
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


    def "directives are printed as top level types when the includeDirectives flag is set"() {
        def simpleIdlWithDirective = '''
                directive @example on FIELD_DEFINITION
                
                directive @moreComplex(arg1 : String = "default", arg2 : Int) 
                    on FIELD_DEFINITION | 
                        INPUT_FIELD_DEFINITION
               
                type Query {
                    fieldA : String @example @moreComplex(arg2 : 666)
                }
            '''
        given:
        def registry = new SchemaParser().parse(simpleIdlWithDirective)
        def runtimeWiring = newRuntimeWiring().build()
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(true)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def resultWithNoDirectives = new SchemaPrinter(defaultOptions().includeDirectives(false)).print(schema)

        then:
        resultWithNoDirectives == '''type Query {
  fieldA: String
}
'''

        when:
        def resultWithSomeDirectives = new SchemaPrinter(defaultOptions().includeDirectives({it.name == "example" })).print(schema)

        then:
        resultWithSomeDirectives == '''directive @example on FIELD_DEFINITION

type Query {
  fieldA: String @example
}
'''

        when:
        def resultWithDirectives = new SchemaPrinter(defaultOptions().includeDirectives(true)).print(schema)

        then:
        resultWithDirectives == '''"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

directive @example on FIELD_DEFINITION

directive @moreComplex(arg1: String = "default", arg2: Int) on FIELD_DEFINITION | INPUT_FIELD_DEFINITION

"Marks the field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ENUM_VALUE

type Query {
  fieldA: String @example @moreComplex(arg1 : "default", arg2 : 666)
}
'''
    }

    def "can print a schema as AST elements"() {
        def sdl = '''
            type Query {
                foo : String
            }
            
            extend type Query {
                bar : String
            }

            extend type Query {
                baz : String
            }

            enum Enum {
                A
            }
            
            extend enum Enum {
                B
            }

            interface Interface {
                foo : String
            }

            extend interface Interface {
                bar : String
            }

            extend interface Interface {
                baz : String
            }
            
            type Foo {
                foo : String
            }
            
            type Bar {
                bar : Scalar
            }

            union Union = Foo
            
            extend union Union = Bar
            
            input Input {
                foo: String
            }
            
            extend input Input {
                bar: String
            }

            extend input Input {
                baz: String
            }

            extend input Input {
                faz: String
            }
            
            scalar Scalar
            
            extend scalar Scalar @directive1
        '''


        when:
        def wiringFactory = new MockedWiringFactory() {
            @Override
            boolean providesScalar(ScalarWiringEnvironment env) {
                return env.getScalarTypeDefinition().getName() == "Scalar"
            }

            @Override
            GraphQLScalarType getScalar(ScalarWiringEnvironment env) {
                def definition = env.getScalarTypeDefinition()
                return GraphQLScalarType.newScalar()
                        .name(definition.getName())
                        .definition(definition)
                        .extensionDefinitions(env.getExtensions())
                        .coercing(TestUtil.mockCoercing())
                        .build()
            }
        }

        def runtimeWiring = newRuntimeWiring()
                .wiringFactory(wiringFactory)
                .build()

        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(false)
        def types = new SchemaParser().parse(sdl)
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(options, types, runtimeWiring)

        def printOptions = defaultOptions().includeScalarTypes(true).useAstDefinitions(true)
        def result = new SchemaPrinter(printOptions).print(schema)

        then:
        result == '''"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Marks the field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ENUM_VALUE

interface Interface {
  foo: String
}

extend interface Interface {
  bar: String
}

extend interface Interface {
  baz: String
}

union Union = Foo

extend union Union = Bar

type Bar {
  bar: Scalar
}

type Foo {
  foo: String
}

type Query {
  foo: String
}

extend type Query {
  bar: String
}

extend type Query {
  baz: String
}

enum Enum {
  A
}

extend enum Enum {
  B
}

scalar Scalar

extend scalar Scalar @directive1

input Input {
  foo: String
}

extend input Input {
  bar: String
}

extend input Input {
  baz: String
}

extend input Input {
  faz: String
}
'''

        when:
        // we can print by direct type using AST
        def queryType = schema.getType("Query")
        result = new SchemaPrinter(printOptions).print(queryType)

        then:
        result == '''type Query {
  foo: String
}

extend type Query {
  bar: String
}

extend type Query {
  baz: String
}

'''
    }

    def "@deprecated directives are always printed"() {
        given:
        def idl = """

            directive @example on FIELD_DEFINITION

            type Field {
              deprecated : Enum @deprecated
            }
            
            type Query {
                field : Field
            }
            
            enum Enum {
              enumVal @deprecated
            }
        """
        def registry = new SchemaParser().parse(idl)
        def runtimeWiring = newRuntimeWiring().build()
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(false)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(defaultOptions().includeDirectives(false)).print(schema)

        then:
        result == '''type Field {
  deprecated: Enum @deprecated(reason : "No longer supported")
}

type Query {
  field: Field
}

enum Enum {
  enumVal @deprecated(reason : "No longer supported")
}
'''
    }

    def "descriptions can be printed as # comments"() {
        given:
        def idl = '''

            """
            This is the docstring of 
            the Query type
            """
            type Query {
                """
                This is the docstring of 
                the fieldX field
                """
              fieldX : String
            }
            
        '''
        def registry = new SchemaParser().parse(idl)
        def runtimeWiring = newRuntimeWiring().build()
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(false)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(defaultOptions().descriptionsAsHashComments(true).includeDirectives(false)).print(schema)

        then:
        result == '''#This is the docstring of 
#the Query type
type Query {
  #This is the docstring of 
  #the fieldX field
  fieldX: String
}
'''
    }

    def "@deprecated directive are always printed regardless of options"() {
        given:
        def idl = '''

            type Query {
              fieldX : String @deprecated
            }
            
        '''
        def registry = new SchemaParser().parse(idl)
        def runtimeWiring = newRuntimeWiring().build()
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(false)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''type Query {
  fieldX: String @deprecated(reason : "No longer supported")
}
'''
    }


}
