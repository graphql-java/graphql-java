package graphql.introspection

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.Assert
import graphql.ExecutionInput
import graphql.ExecutionResultImpl
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.Document
import graphql.language.EnumTypeDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.IntValue
import graphql.language.InterfaceTypeDefinition
import graphql.language.ObjectField
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.UnionTypeDefinition
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaPrinter
import groovy.json.JsonSlurper
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.introspection.IntrospectionQuery.INTROSPECTION_QUERY
import static graphql.language.AstPrinter.printAst
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

class IntrospectionResultToSchemaTest extends Specification {

    def introspectionResultToSchema = new IntrospectionResultToSchema()

    Map<String, Object> slurp(String input) {
        def slurper = new JsonSlurper()
        Map<String, Object> parsed = slurper.parseText(input) as Map<String, Object>
        parsed

    }

    def "create object"() {
        def input = ''' {
            "kind": "OBJECT",
            "name": "QueryType",
            "description": null,
            "fields": [
              {
                "name": "hero",
                "description": null,
                "args": [
                  {
                    "name": "episode",
                    "description": "comment about episode\non two lines",
                    "type": {
                      "kind": "ENUM",
                      "name": "Episode",
                      "ofType": null
                    },
                    "defaultValue": null
                  },
                  {
                    "name": "foo",
                    "description": null,
                    "type": {
                        "kind": "SCALAR",
                        "name": "String",
                        "ofType": null
                    },
                    "defaultValue": "\\"bar\\""
                  }
                ],
                "type": {
                  "kind": "INTERFACE",
                  "name": "Character",
                  "ofType": null
                },
                "isDeprecated": true,
                "deprecationReason": "killed off character"
              }
            ],
            "inputFields": null,
            "interfaces": [{
                    "kind": "INTERFACE",
                    "name": "Query",
                    "ofType": null
                }],
            "enumValues": null,
            "possibleTypes": null
      }
      '''
        def parsed = slurp(input)

        when:
        ObjectTypeDefinition objectTypeDefinition = introspectionResultToSchema.createObject(parsed)
        def result = printAst(objectTypeDefinition)

        then:
        result == """type QueryType implements Query {
  hero(
  \"\"\"
  comment about episode
  on two lines
  \"\"\"
  episode: Episode
  foo: String = \"bar\"): Character @deprecated(reason: "killed off character")
}"""

    }

    def "create interface"() {
        def input = """ {
        "kind": "INTERFACE",
        "name": "Character",
        "description": "A character in the Star Wars Trilogy",
        "fields": [
          {
            "name": "id",
            "description": "The id of the character.",
            "args": [
            ],
            "type": {
              "kind": "NON_NULL",
              "name": null,
              "ofType": {
                "kind": "SCALAR",
                "name": "String",
                "ofType": null
              }
            },
            "isDeprecated": false,
            "deprecationReason": null
          },
          {
            "name": "name",
            "description": "The name of the character.",
            "args": [
            ],
            "type": {
              "kind": "SCALAR",
              "name": "String",
              "ofType": null
            },
            "isDeprecated": false,
            "deprecationReason": null
          },
          {
            "name": "friends",
            "description": "The friends of the character, or an empty list if they have none.",
            "args": [
            ],
            "type": {
              "kind": "LIST",
              "name": null,
              "ofType": {
                "kind": "INTERFACE",
                "name": "Character",
                "ofType": null
              }
            },
            "isDeprecated": false,
            "deprecationReason": null
          },
          {
            "name": "appearsIn",
            "description": "Which movies they appear in.",
            "args": [
            ],
            "type": {
              "kind": "LIST",
              "name": null,
              "ofType": {
                "kind": "ENUM",
                "name": "Episode",
                "ofType": null
              }
            },
            "isDeprecated": false,
            "deprecationReason": null
          }
        ],
        "inputFields": null,
        "interfaces": null,
        "enumValues": null,
        "possibleTypes": [
          {
            "kind": "OBJECT",
            "name": "Human",
            "ofType": null
          },
          {
            "kind": "OBJECT",
            "name": "Droid",
            "ofType": null
          }
        ]
      }
      """
        def parsed = slurp(input)

        when:
        InterfaceTypeDefinition interfaceTypeDefinition = introspectionResultToSchema.createInterface(parsed)
        def result = printAst(interfaceTypeDefinition)

        then:
        result == """"A character in the Star Wars Trilogy"
interface Character {
  id: String!
  name: String
  friends: [Character]
  appearsIn: [Episode]
}"""

    }

    def "create enum"() {
        def input = """ {
        "kind": "ENUM",
        "name": "Episode",
        "description": "One of the films in the Star Wars Trilogy",
        "fields": null,
        "inputFields": null,
        "interfaces": null,
        "enumValues": [
          {
            "name": "NEWHOPE",
            "description": "Released in 1977.",
            "isDeprecated": false,
            "deprecationReason": null
          },
          {
            "name": "EMPIRE",
            "description": "Released in 1980.",
            "isDeprecated": false,
            "deprecationReason": null
          },
          {
            "name": "JEDI",
            "description": "Released in 1983.",
            "isDeprecated": true,
            "deprecationReason": "killed by clones"
          }
        ],
        "possibleTypes": null
      }
      """
        def parsed = slurp(input)

        when:
        EnumTypeDefinition enumTypeDef = introspectionResultToSchema.createEnum(parsed)
        def result = printAst(enumTypeDef)

        then:
        result == """"One of the films in the Star Wars Trilogy"
enum Episode {
  "Released in 1977."
  NEWHOPE
  "Released in 1980."
  EMPIRE
  "Released in 1983."
  JEDI @deprecated(reason: "killed by clones")
}"""

    }

    def "create union"() {
        def input = """ {
          "kind": "UNION",
          "name": "Everything",
          "description": "all the stuff",
          "fields": null,
          "inputFields": null,
          "interfaces": null,
          "enumValues": null,
          "possibleTypes": [
            {
              "kind": "OBJECT",
              "name": "Character",
              "ofType": null
            },
            {
              "kind": "OBJECT",
              "name": "Episode",
              "ofType": null
            }
          ]
        }
      """
        def parsed = slurp(input)

        when:
        UnionTypeDefinition unionTypeDefinition = introspectionResultToSchema.createUnion(parsed)
        def result = printAst(unionTypeDefinition)

        then:
        result == """"all the stuff"
union Everything = Character | Episode"""

    }

    def "create input object"() {
        def input = """ {
        "kind": "INPUT_OBJECT",
        "name": "CharacterInput",
        "description": "input for characters",
        "fields": null,
        "inputFields": [
            {
                "name": "firstName",
                "description": "first name",
                "type": {
                "kind": "SCALAR",
                "name": "String",
                "ofType": null
            },
                "defaultValue": null
            },
            {
                "name": "lastName",
                "description": null,
                "type": {
                "kind": "SCALAR",
                "name": "String",
                "ofType": null
            },
                "defaultValue": null
            },
            {
                "name": "family",
                "description": null,
                "type": {
                "kind": "SCALAR",
                "name": "Boolean",
                "ofType": null
            },
                "defaultValue": null
            }
        ],
        "interfaces": null,
        "enumValues": null,
        "possibleTypes": null
    }
    """
        def parsed = slurp(input)

        when:
        InputObjectTypeDefinition inputObjectTypeDefinition = introspectionResultToSchema.createInputObject(parsed)
        def result = printAst(inputObjectTypeDefinition)

        then:
        result == """"input for characters"
input CharacterInput {
  "first name"
  firstName: String
  lastName: String
  family: Boolean
}"""
    }


    def "create schema"() {
        def input = """{
          "__schema": {
            "queryType": {
              "name": "QueryType"
            },
            "mutationType": {"name":"MutationType"},
            "subscriptionType": {"name":"SubscriptionType"},
            "types": [
            ]
            }"""
        def parsed = slurp(input)

        when:
        Document document = introspectionResultToSchema.createSchemaDefinition(parsed)
        def result = printAst(document)

        then:
        result == """schema {
  query: QueryType
  mutation: MutationType
  subscription: SubscriptionType
}
"""
    }

    def "test starwars introspection result"() {
        given:
        String starwars = this.getClass().getResource('/starwars-introspection.json').text

        def parsed = slurp(starwars)

        when:
        Document document = introspectionResultToSchema.createSchemaDefinition(parsed)
        def result = printAst(document)

        then:
        result == """schema {
  query: QueryType
}

type QueryType {
  hero(
  "If omitted, returns the hero of the whole saga. If provided, returns the hero of that particular episode."
  episode: Episode): Character
  human(
  "id of the human"
  id: String!): Human
  droid(
  "id of the droid"
  id: String!): Droid
}

"A character in the Star Wars Trilogy"
interface Character {
  id: String!
  name: String
  friends: [Character]
  appearsIn: [Episode]
}

"One of the films in the Star Wars Trilogy"
enum Episode {
  "Released in 1977."
  NEWHOPE
  "Released in 1980."
  EMPIRE
  "Released in 1983."
  JEDI
}

"A humanoid creature in the Star Wars universe."
type Human implements Character {
  id: String!
  name: String
  friends: [Character]
  appearsIn: [Episode]
  homePlanet: String
}

"A mechanical creature in the Star Wars universe."
type Droid implements Character {
  id: String!
  name: String
  friends: [Character]
  appearsIn: [Episode]
  primaryFunction: String
}
"""
    }

    def "test simpsons introspection result"() {
        given:
        String simpsons = this.getClass().getResource('/simpsons-introspection.json').text

        def parsed = slurp(simpsons)

        when:
        Document document = introspectionResultToSchema.createSchemaDefinition(parsed)
        def result = printAst(document)

        then:
        result == """schema {
  query: QueryType
  mutation: MutationType
}

type QueryType {
  character(firstName: String): Character
  characters: [Character]
  episodes: [Episode]
  search(searchFor: String): [Everything]
}

type Character {
  id: ID!
  firstName: String
  lastName: String
  family: Boolean
  episodes: [Episode]
}

type Episode {
  id: ID!
  name: String
  season: Season
  number: Int
  numberOverall: Int
  characters: [Character]
}

" Simpson seasons"
enum Season {
  " the beginning"
  Season1
  Season2
  Season3
  Season4
  " Another one"
  Season5
  Season6
  Season7
  Season8
  " Not really the last one :-)"
  Season9
}

union Everything = Character | Episode

type MutationType {
  addCharacter(character: CharacterInput): MutationResult
}

type MutationResult {
  success: Boolean
}

input CharacterInput {
  firstName: String
  lastName: String
  family: Boolean
}
"""
    }

    def "test complete round trip"() {
        given:
        def queryType = GraphQLObjectType.newObject().name("Query").field(newFieldDefinition().name("hello").type(GraphQLString).build())
        GraphQLSchema graphQLSchema = GraphQLSchema.newSchema().query(queryType).build()


        when:
        def options = SchemaPrinter.Options.defaultOptions().includeDirectives(false)
        def printedSchema = new SchemaPrinter(options).print(graphQLSchema)

        def graphQL = TestUtil.graphQL(printedSchema).build()

        def introspectionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(INTROSPECTION_QUERY).build())
        Document schemaDefinitionDocument = introspectionResultToSchema.createSchemaDefinition(introspectionResult.data as Map)
        def astPrinterResult = printAst(schemaDefinitionDocument)

        then:
        printedSchema == astPrinterResult
    }

    def "test argument encoding"() {
        given:
        def schemaSpec = '''
            type OutputType {
                text : String
            }
            
            type Query {
                outputField(
                    inputArg : InputType = {name : "nameViaArg", age : 666}
                    inputBoolean : Boolean = true
                    inputInt : Int = 1
                    inputString : String = "viaArgString"
                    ) : OutputType
            }
            
            input InputType {
                age : Int = -1
                complex : ComplexType = {string : "string", boolean : true,  int : 666}
                name : String = "defaultName"
                rocks  : Boolean = true
            }
            
            input ComplexType {
                boolean : Boolean
                int : Int
                string : String
            }
            '''

        def options = SchemaPrinter.Options.defaultOptions().includeDirectives(false)
        def schema = TestUtil.schema(schemaSpec)
        def printedSchema = new SchemaPrinter(options).print(schema)

        when:
        StringWriter sw = new StringWriter()
        def introspectionResult = GraphQL.newGraphQL(schema).build().execute(ExecutionInput.newExecutionInput().query(INTROSPECTION_QUERY).build())

        //
        // round trip the introspection into JSON and then again to ensure
        // we see any encoding aspects
        //
        ObjectMapper objectMapper = new ObjectMapper()
        objectMapper.writer().writeValue(sw, introspectionResult.data)
        def json = sw.toString()
        def roundTripMap = objectMapper.readValue(json, Map.class)

        Document schemaDefinitionDocument = introspectionResultToSchema.createSchemaDefinition(roundTripMap)

        def astPrinterResult = printAst(schemaDefinitionDocument)

        def actualSchema = TestUtil.schema(astPrinterResult)
        def actualPrintedSchema = new SchemaPrinter(options).print(actualSchema)

        then:
        printedSchema == actualPrintedSchema

        actualPrintedSchema == '''type OutputType {
  text: String
}

type Query {
  outputField(inputArg: InputType = {name : "nameViaArg", age : 666}, inputBoolean: Boolean = true, inputInt: Int = 1, inputString: String = "viaArgString"): OutputType
}

input ComplexType {
  boolean: Boolean
  int: Int
  string: String
}

input InputType {
  age: Int = -1
  complex: ComplexType = {string : "string", boolean : true, int : 666}
  name: String = "defaultName"
  rocks: Boolean = true
}
'''
    }

    def "doesn't create schemaDefinition if not needed"() {
        def input = """ {
          "__schema": {
            "queryType": {
              "name": "Query"
            },
            "mutationType": ${mutationName},
            "subscriptionType": ${subscriptionName},
            "types": [{ 
                "kind": "OBJECT",
                "name": "Query",
                "description": null,
                "fields": [
                  {
                    "name": "hello",
                    "description": null,
                    "args": [],
                    "type": {
                      "kind": "SCALAR",
                      "name": "String",
                      "ofType": null
                    },
                    "isDeprecated": false,
                    "deprecationReason": null
                  }
                ],
                "inputFields": null,
                "interfaces": [],
                "enumValues": null,
                "possibleTypes": null
              }]
          }
          }
      """
        def slurper = new JsonSlurper()
        def parsed = slurper.parseText(input)

        when:
        Document document = introspectionResultToSchema.createSchemaDefinition(parsed)
        def result = printAst(document)

        then:
        result == """type Query {
  hello: String
}
"""

        where:
        mutationName          | subscriptionName
        null                  | null
        '{"name":"Mutation"}' | '{"name":"Subscription"}'
        '{"name":"Mutation"}' | null
        null                  | '{"name":"Subscription"}'
    }

    def "create schema fail"() {
        given:
        def failResult = ExecutionResultImpl.newExecutionResult().build()

        when:
        Document document = introspectionResultToSchema.createSchemaDefinition(failResult)

        then:
        document == null
    }

    def "create scalars"() {
        def input = ''' {
            "kind": "SCALAR",
            "name": "ScalarType",
            "description": "description of ScalarType",
      }
      '''
        def parsed = slurp(input)

        when:
        def scalarTypeDefinition = introspectionResultToSchema.createScalar(parsed)
        def result = printAst(scalarTypeDefinition)

        then:
        result == """"description of ScalarType"\nscalar ScalarType"""
    }

    def " create directives "() {
        def input = '''
                    {
                       "name": "customizedDirective",
                       "locations": [
                            "FIELD",
                            "FRAGMENT_SPREAD",
                            "INLINE_FRAGMENT"
                       ],
                       "args": []
                    }
        '''
        def parsed = slurp(input)

        when:
        def directiveDefinition = introspectionResultToSchema.createDirective(parsed)
        def result = printAst(directiveDefinition)

        then:
        result == """directive @customizedDirective on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT"""
    }

    def "create directives with arguments and default value"() {
        def input = '''{
            "name": "customizedDirective",
            "description": "customized directive",
            "locations": [
                "FIELD",
                "FRAGMENT_SPREAD",
                "INLINE_FRAGMENT"
            ],
            "args": [
                  {
                    "name": "directiveArg",
                    "description": "directive arg",
                    "type": {
                      "kind": "SCALAR",
                      "name": "String",
                      "ofType": null
                    },
                    "isDeprecated": false,
                    "deprecationReason": null,
                    "defaultValue": "\\"default Value\\""
                  }
             ]
        }
      '''
        def parsed = slurp(input)

        when:
        def directiveDefinition = introspectionResultToSchema.createDirective(parsed)
        def result = printAst(directiveDefinition)

        then:
        result == """"customized directive"
directive @customizedDirective("directive arg"
directiveArg: String = "default Value") on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT"""
    }

    def "create schema with directives"() {
        def input = """{
          "__schema": {
            "queryType": {
              "name": "QueryType"
            },
            "types": [],
            "directives": [
                {
                    "name": "customizedDirective",
                    "description": "customized directive",
                    "locations": [
                        "FIELD",
                        "FRAGMENT_SPREAD",
                        "INLINE_FRAGMENT"
                    ],
                    "args": [
                          {
                            "name": "directiveArg",
                            "description": "directive arg",
                            "type": {
                              "kind": "SCALAR",
                              "name": "String",
                              "ofType": null
                            },
                            "isDeprecated": false,
                            "deprecationReason": null,
                            "defaultValue": "\\"default Value\\""
                          }
                     ]
                },
                {
                    "name": "repeatableDirective",
                    "description": "repeatable directive",
                    "locations": [
                        "FIELD_DEFINITION"
                    ],
                    "args": [],
                    "isRepeatable":true
                }
            ]
         }"""
        def parsed = slurp(input)

        when:
        Document document = introspectionResultToSchema.createSchemaDefinition(parsed)
        def result = printAst(document)

        then:
        result == """schema {
  query: QueryType
}

"customized directive"
directive @customizedDirective("directive arg"
directiveArg: String = "default Value") on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT

"repeatable directive"
directive @repeatableDirective repeatable on FIELD_DEFINITION
"""
    }

    def "round trip of default values of complex custom Scalar via SDL"() {
        given:
        def employeeRefScalar = GraphQLScalarType.newScalar().name("EmployeeRef").coercing(new Coercing() {
            @Override
            Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return null
            }

            @Override
            Object parseValue(Object input) throws CoercingParseValueException {
                return null
            }

            @Override
            Object parseLiteral(Object input) throws CoercingParseLiteralException {
                return null
            }

            @Override
            Value valueToLiteral(Object input) {
                return null
            }
        }).build()

        def sdl = '''
            scalar EmployeeRef
            type Query{
                foo(arg: EmployeeRef = {externalRef: "123", department: 5}): String 
            }
        '''
        def options = SchemaPrinter.Options.defaultOptions().includeDirectives(false)
        def rw = RuntimeWiring.newRuntimeWiring().scalar(employeeRefScalar).build()
        def schema = TestUtil.schema(sdl, rw)
        def printedSchema = new SchemaPrinter(options).print(schema)

        when:
        StringWriter sw = new StringWriter()
        def introspectionResult = GraphQL.newGraphQL(schema).build().execute(ExecutionInput.newExecutionInput().query(INTROSPECTION_QUERY).build())

        //
        // round trip the introspection into JSON and then again to ensure
        // we see any encoding aspects
        //
        ObjectMapper objectMapper = new ObjectMapper()
        objectMapper.writer().writeValue(sw, introspectionResult.data)
        def json = sw.toString()
        def roundTripMap = objectMapper.readValue(json, Map.class)
        Document schemaDefinitionDocument = introspectionResultToSchema.createSchemaDefinition(roundTripMap)

        def astPrinterResult = printAst(schemaDefinitionDocument)

        def actualSchema = TestUtil.schema(astPrinterResult, rw)
        def actualPrintedSchema = new SchemaPrinter(options).print(actualSchema)

        then:
        printedSchema == actualPrintedSchema

        actualPrintedSchema == '''type Query {
  foo(arg: EmployeeRef = {externalRef : "123", department : 5}): String
}

scalar EmployeeRef
'''
    }

    class ExternalEmployeeRef {
        String externalRef;
        String externalDepartment;
    }

    def "round trip of default values of complex custom Scalar via programmatic schema"() {
        given:
        def employeeRefScalar = GraphQLScalarType.newScalar().name("EmployeeRef").coercing(new Coercing() {
            @Override
            Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                return null
            }

            @Override
            Object parseValue(Object input) throws CoercingParseValueException {
                return null
            }

            @Override
            Object parseLiteral(Object input) throws CoercingParseLiteralException {
                return null
            }

            @Override
            Value valueToLiteral(Object input) {
                if (input instanceof ExternalEmployeeRef) {
                    def externalRef = StringValue.newStringValue(input.externalRef).build()
                    def refField = ObjectField.newObjectField().name("ref").value(externalRef).build()
                    def externalDepartment = IntValue.newIntValue(new BigInteger(input.externalDepartment)).build()
                    def departmentField = ObjectField.newObjectField().name("department").value(externalDepartment).build()
                    return ObjectValue.newObjectValue().objectField(refField).objectField(departmentField).build()
                }
                return Assert.assertShouldNeverHappen();
            }
        }).build()


        def ref = new ExternalEmployeeRef(externalRef: "123", externalDepartment: "5")
        def argument = GraphQLArgument.newArgument().name("arg").type(employeeRefScalar).defaultValueProgrammatic(ref).build()
        def field = newFieldDefinition().name("foo").type(GraphQLString).argument(argument).build()
        def queryType = GraphQLObjectType.newObject().name("Query").field(field).build()
        def schema = GraphQLSchema.newSchema().query(queryType).build()

        def options = SchemaPrinter.Options.defaultOptions().includeDirectives(false)
        def printedSchema = new SchemaPrinter(options).print(schema)

        when:
        StringWriter sw = new StringWriter()
        def introspectionResult = GraphQL.newGraphQL(schema).build().execute(ExecutionInput.newExecutionInput().query(INTROSPECTION_QUERY).build())

        //
        // round trip the introspection into JSON and then again to ensure
        // we see any encoding aspects
        //
        ObjectMapper objectMapper = new ObjectMapper()
        objectMapper.writer().writeValue(sw, introspectionResult.data)
        def json = sw.toString()
        def roundTripMap = objectMapper.readValue(json, Map.class)
        Document schemaDefinitionDocument = introspectionResultToSchema.createSchemaDefinition(roundTripMap)

        def astPrinterResult = printAst(schemaDefinitionDocument)

        def rw = RuntimeWiring.newRuntimeWiring().scalar(employeeRefScalar).build()
        def actualSchema = TestUtil.schema(astPrinterResult, rw)
        def actualPrintedSchema = new SchemaPrinter(options).print(actualSchema)

        then:
        printedSchema == actualPrintedSchema

        actualPrintedSchema == '''type Query {
  foo(arg: EmployeeRef = {ref : "123", department : 5}): String
}

scalar EmployeeRef
'''
    }
}