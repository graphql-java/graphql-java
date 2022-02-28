package graphql.schema.idl

import graphql.GraphQL
import graphql.Scalars
import graphql.TestUtil
import graphql.TypeResolutionEnvironment
import graphql.introspection.IntrospectionQuery
import graphql.introspection.IntrospectionResultToSchema
import graphql.schema.Coercing
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLNamedSchemaElement
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLType
import graphql.schema.GraphQLUnionType
import graphql.schema.GraphqlTypeComparatorRegistry
import graphql.schema.GraphqlTypeComparators
import graphql.schema.TypeResolver
import spock.lang.Specification

import java.util.function.Predicate
import java.util.function.UnaryOperator

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mockScalar
import static graphql.TestUtil.mockTypeRuntimeWiring
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.SchemaPrinter.ExcludeGraphQLSpecifiedDirectivesPredicate
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


    GraphQLScalarType ASTEROID = newScalar().name("Asteroid").description("desc").coercing(new Coercing() {
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
            .build()

    def resolver = new TypeResolver() {

        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    def "typeString"() {

        GraphQLType type1 = nonNull(list(nonNull(list(nonNull(Scalars.GraphQLInt)))))

        def typeStr1 = new SchemaPrinter().typeString(type1)

        expect:
        typeStr1 == "[[Int!]!]!"
    }

    def "argsString"() {
        def argument1 = newArgument().name("arg1").type(list(nonNull(GraphQLInt))).defaultValue(10).build()
        def argument2 = newArgument().name("arg2").type(GraphQLString).build();
        def argument3 = newArgument().name("arg3").type(GraphQLString).defaultValue("default").build()
        def argStr = new SchemaPrinter().argsString([argument1, argument2, argument3])

        expect:

        argStr == "(arg1: [Int!] = 10, arg2: String, arg3: String = \"default\")"
    }

    def "argsString_sorts"() {
        def argument1 = newArgument().name("arg1").type(list(nonNull(GraphQLInt))).defaultValue(10).build()
        def argument2 = newArgument().name("arg2").type(GraphQLString).build();
        def argument3 = newArgument().name("arg3").type(GraphQLString).defaultValue("default").build()
        def argStr = new SchemaPrinter().argsString([argument2, argument1, argument3])

        expect:

        argStr == "(arg1: [Int!] = 10, arg2: String, arg3: String = \"default\")"
    }

    def "argsString_comments"() {
        def argument1 = newArgument().name("arg1").description("A multiline\ncomment").type(list(nonNull(GraphQLInt))).defaultValue(10).build()
        def argument2 = newArgument().name("arg2").description("A single line comment").type(list(nonNull(GraphQLInt))).defaultValue(10).build()
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
        def queryType = newObject().name("Query").description("About Query\nSecond Line").field(fieldDefinition).build()
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
        def queryType = newObject().name("Query").field(fieldDefinition).build()
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
        def queryType = newObject().name("Query").field(fieldDefinition).build()
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
        def queryType = newObject().name("Query").field(fieldDefinition).build()
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
        def possibleType = newObject().name("PossibleType").field(fieldDefinition).build()
        GraphQLUnionType unionType = GraphQLUnionType.newUnionType()
                .name("Union")
                .description("About union")
                .possibleType(possibleType)
                .typeResolver({ it -> null })
                .build()
        GraphQLFieldDefinition fieldDefinition2 = newFieldDefinition()
                .name("field").type(unionType).build()
        def queryType = newObject().name("Query").field(fieldDefinition2).build()
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
        def possibleType1 = newObject().name("PossibleType1").field(
                newFieldDefinition().name("field").type(GraphQLString).build()
        ).build()
        def possibleType2 = newObject().name("PossibleType2").field(
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
        def queryType = newObject().name("Query").field(fieldDefinition2).build()
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
        def queryType = newObject().name("Query").field(fieldDefinition2).build()
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
        def queryType = newObject().name("Query").field(fieldDefinition).build()
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
        GraphQLScalarType myScalar = newScalar().name("Scalar").description("about scalar").coercing(new Coercing() {
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
                .build()
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field").type(myScalar).build()
        def queryType = newObject().name("Query").field(fieldDefinition).build()
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
        def queryType = newObject().name("Query").field(fieldDefinition2).build()
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

    def "prints type"() {
        given:
        def inputObjectType = GraphQLInputObjectType.newInputObject()
                .name("inputObjectType")
                .field(GraphQLInputObjectField.newInputObjectField().name("field").type(GraphQLString).build())
                .build()
        def objectType = newObject()
                .name("objectType")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString).build())
                .build()
        def argument = GraphQLArgument.newArgument().name("arg").type(inputObjectType).build()
        GraphQLFieldDefinition field1 = newFieldDefinition().name("field1").type(objectType).argument(argument).build()

        def interfaceType = GraphQLInterfaceType.newInterface()
                .name("interfaceType")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString).build())
                .build()
        def objectWithInterface = newObject()
                .name("objectWithInterface")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("field").type(GraphQLString).build())
                .withInterface(interfaceType)
                .build()
        GraphQLFieldDefinition field2 = newFieldDefinition()
                .name("field2")
                .type(objectWithInterface)
                .build()

        def enumType = GraphQLEnumType.newEnum()
                .name("enumType")
                .value(GraphQLEnumValueDefinition.newEnumValueDefinition().name("GraphQLEnumValueDefinition").build())
                .build()
        GraphQLFieldDefinition field3 = newFieldDefinition()
                .name("field3")
                .type(enumType)
                .build()

        def queryType = newObject().name("Query").field(field1).field(field2).field(field3).build()
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry().typeResolver(interfaceType, { env -> null }).build();
        def schema = GraphQLSchema.newSchema().query(queryType).codeRegistry(codeRegistry).build()
        when:
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''interface interfaceType {
  field: String
}

type Query {
  field1(arg: inputObjectType): objectType
  field2: objectWithInterface
  field3: enumType
}

type objectType {
  field: String
}

type objectWithInterface implements interfaceType {
  field: String
}

enum enumType {
  GraphQLEnumValueDefinition
}

input inputObjectType {
  field: String
}
'''
    }

    def "arrayIndexOutOfBoundsException should not occur if a field description of only a newline is passed"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("field")
                .description("\n")
                .type(GraphQLString)
                .build()

        def queryType = newObject().name("Query").description("test").field(fieldDefinition).build()
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

"desc"
scalar Asteroid
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
            directive @interfaceFieldDirective on FIELD_DEFINITION
            directive @unionTypeDirective on UNION
            directive @query1 repeatable on OBJECT
            directive @query2(arg1: String) on OBJECT
            directive @fieldDirective1 on FIELD_DEFINITION
            directive @fieldDirective2(argStr: String, argInt: Int, argFloat: Float, argBool: Boolean) on FIELD_DEFINITION
            directive @argDirective on ARGUMENT_DEFINITION
            directive @interfaceImplementingTypeDirective on OBJECT
            directive @enumTypeDirective on ENUM
            directive @single on OBJECT
            directive @singleField on FIELD_DEFINITION
            directive @interfaceImplementingFieldDirective on FIELD_DEFINITION
            directive @enumValueDirective on ENUM_VALUE
            directive @inputTypeDirective on INPUT_OBJECT
            directive @inputFieldDirective on INPUT_FIELD_DEFINITION
            directive @interfaceTypeDirective on INTERFACE
            directive @scalarDirective on SCALAR
            directive @repeatableDirective repeatable on SCALAR
            
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
            
            scalar SomeScalar @scalarDirective @repeatableDirective @repeatableDirective
            
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
        def options = SchemaGenerator.Options.defaultOptions()
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(defaultOptions().includeScalarTypes(true)).print(schema)

        then:
        // args and directives are sorted like the rest of the schema printer
        result == '''directive @argDirective on ARGUMENT_DEFINITION

"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

directive @enumTypeDirective on ENUM

directive @enumValueDirective on ENUM_VALUE

directive @fieldDirective1 on FIELD_DEFINITION

directive @fieldDirective2(argBool: Boolean, argFloat: Float, argInt: Int, argStr: String) on FIELD_DEFINITION

"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

directive @inputFieldDirective on INPUT_FIELD_DEFINITION

directive @inputTypeDirective on INPUT_OBJECT

directive @interfaceFieldDirective on FIELD_DEFINITION

directive @interfaceImplementingFieldDirective on FIELD_DEFINITION

directive @interfaceImplementingTypeDirective on OBJECT

directive @interfaceTypeDirective on INTERFACE

directive @query1 repeatable on OBJECT

directive @query2(arg1: String) on OBJECT

directive @repeatableDirective repeatable on SCALAR

directive @scalarDirective on SCALAR

directive @single on OBJECT

directive @singleField on FIELD_DEFINITION

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Exposes a URL that specifies the behaviour of this scalar."
directive @specifiedBy(
    "The URL that specifies the behaviour of this scalar."
    url: String!
  ) on SCALAR

directive @unionTypeDirective on UNION

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

scalar SomeScalar @repeatableDirective @repeatableDirective @scalarDirective

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
              deprecatedFieldArgument( arg1 : String, arg2 : Int @deprecated) : Enum
              deprecatedFieldArgumentWithReason( arg1 : String, arg2 : Int @deprecated(reason : "Custom arg reason 1")) : Enum
            }
            
            type Query {
                field : Field
            }
            
            enum Enum {
              ACTIVE
              DEPRECATED @deprecated
              DEPRECATED_WITH_REASON @deprecated(reason : "Custom reason 2")
            }
            
            input Input {
              active : Enum
              deprecated : Enum @deprecated
              deprecatedWithReason : Enum @deprecated(reason : "Custom reason 3")
          }
        """
        def registry = new SchemaParser().parse(idl)
        def runtimeWiring = newRuntimeWiring().build()
        def options = SchemaGenerator.Options.defaultOptions()
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(defaultOptions().includeScalarTypes(true)).print(schema)

        then:
        // args and directives are sorted like the rest of the schema printer
        result == '''"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Exposes a URL that specifies the behaviour of this scalar."
directive @specifiedBy(
    "The URL that specifies the behaviour of this scalar."
    url: String!
  ) on SCALAR

type Field {
  active: Enum
  deprecated: Enum @deprecated(reason : "No longer supported")
  deprecatedFieldArgument(arg1: String, arg2: Int @deprecated(reason : "No longer supported")): Enum
  deprecatedFieldArgumentWithReason(arg1: String, arg2: Int @deprecated(reason : "Custom arg reason 1")): Enum
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

input Input {
  active: Enum
  deprecated: Enum @deprecated(reason : "No longer supported")
  deprecatedWithReason: Enum @deprecated(reason : "Custom reason 3")
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
        def options = SchemaGenerator.Options.defaultOptions()
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def resultWithNoDirectives = new SchemaPrinter(defaultOptions().includeDirectives(false)).print(schema)

        then:
        resultWithNoDirectives == '''type Query {
  fieldA: String
}
'''

        when:
        def resultWithSomeDirectives = new SchemaPrinter(defaultOptions().includeDirectives({ it == "example" })).print(schema)

        then:
        resultWithSomeDirectives == '''directive @example on FIELD_DEFINITION

type Query {
  fieldA: String @example
}
'''

        when:
        def resultWithDirectives = new SchemaPrinter(defaultOptions().includeDirectives(true)).print(schema)

        then:
        resultWithDirectives == '''"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

directive @example on FIELD_DEFINITION

"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

directive @moreComplex(arg1: String = "default", arg2: Int) on FIELD_DEFINITION | INPUT_FIELD_DEFINITION

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Exposes a URL that specifies the behaviour of this scalar."
directive @specifiedBy(
    "The URL that specifies the behaviour of this scalar."
    url: String!
  ) on SCALAR

type Query {
  fieldA: String @example @moreComplex(arg1 : "default", arg2 : 666)
}
'''
    }

    def "directive definitions are not printed when the includeDirectiveDefinitions flag is set to false"() {
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
        def options = SchemaGenerator.Options.defaultOptions()
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def resultWithNoDirectiveDefinitions = new SchemaPrinter(defaultOptions().includeDirectiveDefinitions(false)).print(schema)

        then:
        resultWithNoDirectiveDefinitions == '''type Query {
  fieldA: String @example @moreComplex(arg1 : "default", arg2 : 666)
}
'''

        when:
        def resultWithDirectiveDefinitions = new SchemaPrinter(defaultOptions().includeDirectiveDefinitions(true)).print(schema)

        then:
        resultWithDirectiveDefinitions == '''"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

directive @example on FIELD_DEFINITION

"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

directive @moreComplex(arg1: String = "default", arg2: Int) on FIELD_DEFINITION | INPUT_FIELD_DEFINITION

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Exposes a URL that specifies the behaviour of this scalar."
directive @specifiedBy(
    "The URL that specifies the behaviour of this scalar."
    url: String!
  ) on SCALAR

type Query {
  fieldA: String @example @moreComplex(arg1 : "default", arg2 : 666)
}
'''
    }

    def "can print a schema as AST elements"() {
        def sdl = '''
            directive @directive1 on SCALAR
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
                return newScalar()
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

        def options = SchemaGenerator.Options.defaultOptions()
        def types = new SchemaParser().parse(sdl)
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(options, types, runtimeWiring)

        def printOptions = defaultOptions().includeScalarTypes(true).useAstDefinitions(true)
        def result = new SchemaPrinter(printOptions).print(schema)

        then:
        result == '''"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

directive @directive1 on SCALAR

"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Exposes a URL that specifies the behaviour of this scalar."
directive @specifiedBy(
    "The URL that specifies the behaviour of this scalar."
    url: String!
  ) on SCALAR

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
            
            input Input {
                deprecated : String @deprecated(reason : "custom reason")
            }
        """
        def registry = new SchemaParser().parse(idl)
        def runtimeWiring = newRuntimeWiring().build()
        def options = SchemaGenerator.Options.defaultOptions()
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

input Input {
  deprecated: String @deprecated(reason : "custom reason")
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
        def options = SchemaGenerator.Options.defaultOptions()
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
        def options = SchemaGenerator.Options.defaultOptions()
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        then:
        result == '''type Query {
  fieldX: String @deprecated(reason : "No longer supported")
}
'''
    }

    def "omit unused built-in scalars by default - created by sdl string"() {
        given:
        def sdl = '''type Query {scalarcustom : RandomScalar} scalar RandomScalar'''

        def registry = new SchemaParser().parse(sdl)
        def runtimeWiring = newRuntimeWiring().scalar(mockScalar("RandomScalar")).build()
        def options = SchemaGenerator.Options.defaultOptions()

        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        expect:

        ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS.forEach({
            scalarType -> assert !result.contains(scalarType.name)
        })

        result == '''type Query {
  scalarcustom: RandomScalar
}

"RandomScalar"
scalar RandomScalar
'''
    }

    def "show unused custom scalars when unused - created by sdl string"() {
        given:
        def sdl = '''type Query {astring : String aInt : Int} "Some Scalar" scalar CustomScalar'''

        def registry = new SchemaParser().parse(sdl)
        def runtimeWiring = newRuntimeWiring().scalar(mockScalar("CustomScalar")).build()
        def options = SchemaGenerator.Options.defaultOptions()

        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        def result = new SchemaPrinter(noDirectivesOption).print(schema)

        expect:
        assert !result.contains("ID") && !result.contains("Float") && !result.contains("Boolean")
        result ==
                '''type Query {
  aInt: Int
  astring: String
}

"CustomScalar"
scalar CustomScalar
'''
    }

    def "omit unused built-in by default - created programmatically"() {
        given:
        GraphQLScalarType myScalar = newScalar().name("RandomScalar").description("about scalar").coercing(new Coercing() {
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
                .build()

        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("scalarType").type(myScalar).build()
        def queryType = newObject().name("Query").field(fieldDefinition).build()

        def schema = GraphQLSchema.newSchema().query(queryType).additionalType(myScalar).build()

        def result = new SchemaPrinter(defaultOptions().includeDirectives(false)).print(schema)

        expect:
        ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS.forEach({
            scalarType -> assert !result.contains(scalarType.name)
        })
        result ==
                '''type Query {
  scalarType: RandomScalar
}

"about scalar"
scalar RandomScalar
'''
    }

    def "show unused custom scalars when unused - created programmatically"() {
        given:
        GraphQLScalarType myScalar = newScalar().name("Scalar").description("about scalar").coercing(new Coercing() {
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
                .build()

        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("someType").type(GraphQLInt).build()
        def queryType = newObject().name("Query").field(fieldDefinition).build()

        def schema = GraphQLSchema.newSchema().query(queryType).additionalType(myScalar).build()

        def result = new SchemaPrinter(defaultOptions().includeScalarTypes(true).includeDirectives(false)).print(schema)

        expect:
        result ==
                '''type Query {
  someType: Int
}

"about scalar"
scalar Scalar
'''
    }

    def "single line comments are properly escaped"() {
        given:
        def idl = """
            type Query {
              "$comment"
              fieldX : String
            }
        """
        def registry = new SchemaParser().parse(idl)
        def runtimeWiring = newRuntimeWiring().build()
        def options = SchemaGenerator.Options.defaultOptions()
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)

        when:
        def result = new SchemaPrinter(defaultOptions().includeDirectives(false)).print(schema)

        then:
        result == """type Query {
  "$comment"
  fieldX: String
}
"""

        where:
        _ | comment
        _ | 'quotation-\\"'
        _ | 'reverse-solidus-\\\\'
        _ | 'backspace-\\b'
        _ | 'formfeed-\\f'
        _ | 'carriage-return-\\r'
        _ | 'horizontal-tab-\\t'
    }

    def 'print interfaces implementing interfaces correctly'() {
        given:
        def sdl = """
            type Query {
                foo: Resource
            }
            
            interface Node {
              id: ID!
            }
            interface Node2 {
              id2: ID!
            }

            interface Resource implements Node & Node2 {
              id: ID!
              id2: ID!
            }
            """
        def schema = TestUtil.schema(sdl)
        when:
        def result = new SchemaPrinter(defaultOptions().includeDirectives(false)).print(schema)

        then:
        result == """interface Node {
  id: ID!
}

interface Node2 {
  id2: ID!
}

interface Resource implements Node & Node2 {
  id: ID!
  id2: ID!
}

type Query {
  foo: Resource
}
"""
    }

    def "schema element filtering works"() {
        def sdl = """
            schema {
                query : PrintMeQuery
            }
            
            directive @directivePrintMe on ARGUMENT_DEFINITION
            directive @someOtherDirective on FIELD_DEFINITION
             
            type PrintMeQuery {
                field : PrintMeType
                fieldPrintMe : SomeType
                fieldPrintMeWithArgs(arg1 : String, arg2PrintMe : String @directivePrintMe) : SomeType @someOtherDirective
            }
            
            type PrintMeType {
                fieldPrintMe : String
            }
            
            type SomeType {
                fieldPrintMe : String
            }
            
        """
        def schema = TestUtil.schema(sdl)

        when:
        Predicate<GraphQLSchemaElement> predicate = { element ->
            if (element instanceof GraphQLNamedSchemaElement) {
                def printIt = ((GraphQLNamedSchemaElement) element).getName().contains("PrintMe")
                return printIt
            }
            return false
        }
        def result = new SchemaPrinter(defaultOptions().includeDirectives(true).includeSchemaElement(predicate)).print(schema)

        then:
        result == """schema {
  query: PrintMeQuery
}

directive @directivePrintMe on ARGUMENT_DEFINITION

type PrintMeQuery {
  fieldPrintMe: SomeType
  fieldPrintMeWithArgs(arg2PrintMe: String @directivePrintMe): SomeType
}

type PrintMeType {
  fieldPrintMe: String
}
"""

    }

    def "schema with directive prints directive"() {
        def sdl = """
            directive @foo on SCHEMA
            type MyQuery { anything: String }
            schema @foo {
                query: MyQuery
            }
        """
        def schema = TestUtil.schema(sdl)

        when:
        def result = new SchemaPrinter(defaultOptions().includeDirectives(true)).print(schema)

        then:
        result == """schema @foo{
  query: MyQuery
}

"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

directive @foo on SCHEMA

"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Exposes a URL that specifies the behaviour of this scalar."
directive @specifiedBy(
    "The URL that specifies the behaviour of this scalar."
    url: String!
  ) on SCALAR

type MyQuery {
  anything: String
}
"""
    }

    def "allow printing of just directives"() {
        def sdl = """
            directive @foo on FIELD_DEFINITION
            type Query { anything: String @foo }
        """
        def schema = TestUtil.schema(sdl)
        def directive = schema.getDirective("foo");

        when:
        def result = new SchemaPrinter(defaultOptions().includeDirectives(true)).print(directive)

        then:
        result == """directive @foo on FIELD_DEFINITION"""
    }

    def "directive with leading pipe gets discarded"() {
        def sdl = """
            directive @foo on | OBJECT | FIELD_DEFINITION
            type Query { anything: String @foo }
        """
        def schema = TestUtil.schema(sdl)
        def directive = schema.getDirective("foo");

        when:
        def result = new SchemaPrinter(defaultOptions().includeDirectives(true)).print(directive)

        then:
        result == """directive @foo on OBJECT | FIELD_DEFINITION"""
    }

    def "description printing escapes triple quotes"() {
        def descriptionWithTripleQuote = 'Hello """ \n World """ """';
        def field = newFieldDefinition().name("hello").type(GraphQLString).build()
        def queryType = newObject().name("Query").field(field).description(descriptionWithTripleQuote).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()
        when:
        def result = new SchemaPrinter(defaultOptions().includeDirectives(ExcludeGraphQLSpecifiedDirectivesPredicate)).print(schema)

        then:
        result == '''"""
Hello \\""" 
 World \\""" \\"""
"""
type Query {
  hello: String
}
'''
    }

    def "directive with optional values"() {
        def sdl = """
            directive @foo(note:String) on FIELD_DEFINITION
            type Query { 
                anything: String @foo 
                anything2: String @foo(note: "foo")
            }
        """
        def schema = TestUtil.schema(sdl)
        when:
        def result = new SchemaPrinter(defaultOptions().includeDirectives(ExcludeGraphQLSpecifiedDirectivesPredicate)).print(schema)


        then:
        result == """directive @foo(note: String) on FIELD_DEFINITION

type Query {
  anything: String @foo
  anything2: String @foo(note : "foo")
}
"""
    }

    def "programmatic object value in an argument is printed"() {

        GraphQLInputObjectType compoundType = GraphQLInputObjectType.newInputObject().name("Compound")
                .field({ it.name("a").type(GraphQLString) })
                .field({ it.name("b").type(GraphQLString) })
                .build()

        GraphQLObjectType objType = newObject().name("obj")
                .field({
                    it.name("f").type(GraphQLString)
                            .argument({
                                it.name("arg").type(compoundType).defaultValueProgrammatic(["a": "A", "b": "B"])
                            })
                }).build()

        when:

        def result = new SchemaPrinter().print(objType)


        then:
        result == '''type obj {
  f(arg: Compound = {a : "A", b : "B"}): String
}

'''

        when:
        def newDirective = GraphQLDirective.newDirective().name("foo")
                .argument({
                    it.name("arg").type(compoundType).valueProgrammatic(["a": "A", "b": "B"])
                })
                .build()

        objType = newObject().name("obj").field({
            it.name("f").type(GraphQLString).withDirective(newDirective)
        }).build()

        result = new SchemaPrinter().print(objType)

        then:

        result == '''type obj {
  f: String @foo(arg : {a : "A", b : "B"})
}

'''
    }

    def "can specify a new ordering for the schema printer"() {

        def sdl = """
            type Query { id( b:ID a:ID c:ID) : ID }
            
            type XQuery { id: ID }
            type YQuery { id: ID }
            type ZQuery { id: ID }
            
            interface XInterface { id: ID }
            interface ZInterface { id: ID }
            interface YInterface { id: ID }
            
            
            input XInput { x : Int }
            input ZInput { x : Int }
            input YInput { x : Int }
            
            scalar XScalar
            scalar ZScalar
            scalar YScalar
            
            union XUnion = Query | XQuery
            union ZUnion = Query | XQuery
            union YUnion = Query | XQuery
        """
        def schema = TestUtil.schema(sdl)

        // by name descending
        GraphqlTypeComparatorRegistry comparatorRegistry = { env -> return GraphqlTypeComparators.byNameAsc().reversed() }
        def options = defaultOptions().includeDirectives(true).setComparators(comparatorRegistry)
        when:
        def result = new SchemaPrinter(options).print(schema)

        then:
        result == '''"Exposes a URL that specifies the behaviour of this scalar."
directive @specifiedBy(
    "The URL that specifies the behaviour of this scalar."
    url: String!
  ) on SCALAR

"Directs the executor to skip this field or fragment when the `if`'argument is true."
directive @skip(
    "Skipped when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Directs the executor to include this field or fragment only when the `if` argument is true"
directive @include(
    "Included when true."
    if: Boolean!
  ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"Marks the field, argument, input field or enum value as deprecated"
directive @deprecated(
    "The reason for the deprecation"
    reason: String = "No longer supported"
  ) on FIELD_DEFINITION | ARGUMENT_DEFINITION | ENUM_VALUE | INPUT_FIELD_DEFINITION

union ZUnion = XQuery | Query

scalar ZScalar

type ZQuery {
  id: ID
}

interface ZInterface {
  id: ID
}

input ZInput {
  x: Int
}

union YUnion = XQuery | Query

scalar YScalar

type YQuery {
  id: ID
}

interface YInterface {
  id: ID
}

input YInput {
  x: Int
}

union XUnion = XQuery | Query

scalar XScalar

type XQuery {
  id: ID
}

interface XInterface {
  id: ID
}

input XInput {
  x: Int
}

type Query {
  id(c: ID, b: ID, a: ID): ID
}
'''

    }
}