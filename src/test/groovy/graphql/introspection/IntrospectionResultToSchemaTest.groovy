package graphql.introspection

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.EnumTypeDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.UnionTypeDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaPrinter
import groovy.json.JsonSlurper
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.introspection.IntrospectionQuery.INTROSPECTION_QUERY
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
                    "description": "comment about episode",
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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(objectTypeDefinition)

        then:
        result == """type QueryType implements Query {
  hero(
  #comment about episode
  episode: Episode
  foo: String = \"bar\"
  ): Character @deprecated(reason: "killed off character")
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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(interfaceTypeDefinition)

        then:
        result == """#A character in the Star Wars Trilogy
interface Character {
  #The id of the character.
  id: String!
  #The name of the character.
  name: String
  #The friends of the character, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(enumTypeDef)

        then:
        result == """#One of the films in the Star Wars Trilogy
enum Episode {
  #Released in 1977.
  NEWHOPE
  #Released in 1980.
  EMPIRE
  #Released in 1983.
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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(unionTypeDefinition)

        then:
        result == """#all the stuff
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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(inputObjectTypeDefinition)

        then:
        result == """#input for characters
input CharacterInput {
  #first name
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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(document)

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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(document)

        then:
        result == """schema {
  query: QueryType
}

type QueryType {
  hero(
  #If omitted, returns the hero of the whole saga. If provided, returns the hero of that particular episode.
  episode: Episode
  ): Character
  human(
  #id of the human
  id: String!
  ): Human
  droid(
  #id of the droid
  id: String!
  ): Droid
}

#A character in the Star Wars Trilogy
interface Character {
  #The id of the character.
  id: String!
  #The name of the character.
  name: String
  #The friends of the character, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
}

#One of the films in the Star Wars Trilogy
enum Episode {
  #Released in 1977.
  NEWHOPE
  #Released in 1980.
  EMPIRE
  #Released in 1983.
  JEDI
}

#A humanoid creature in the Star Wars universe.
type Human implements Character {
  #The id of the human.
  id: String!
  #The name of the human.
  name: String
  #The friends of the human, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
  #The home planet of the human, or null if unknown.
  homePlanet: String
}

#A mechanical creature in the Star Wars universe.
type Droid implements Character {
  #The id of the droid.
  id: String!
  #The name of the droid.
  name: String
  #The friends of the droid, or an empty list if they have none.
  friends: [Character]
  #Which movies they appear in.
  appearsIn: [Episode]
  #The primary function of the droid.
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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(document)

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

# Simpson seasons
enum Season {
  # the beginning
  Season1
  Season2
  Season3
  Season4
  # Another one
  Season5
  Season6
  Season7
  Season8
  # Not really the last one :-)
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
        def printedSchema = new SchemaPrinter().print(graphQLSchema)

        GraphQLSchema schema = TestUtil.schema(printedSchema)

        def introspectionResult = GraphQL.newGraphQL(schema).build().execute(ExecutionInput.newExecutionInput().query(INTROSPECTION_QUERY).build())
        Document schemaDefinitionDocument = introspectionResultToSchema.createSchemaDefinition(introspectionResult.data as Map)
        AstPrinter astPrinter = new AstPrinter()
        def astPrinterResult = astPrinter.printAst(schemaDefinitionDocument)

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

        def schema = TestUtil.schema(schemaSpec)
        def printedSchema = new SchemaPrinter().print(schema)

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

        AstPrinter astPrinter = new AstPrinter()
        def astPrinterResult = astPrinter.printAst(schemaDefinitionDocument)

        def actualSchema = TestUtil.schema(astPrinterResult)
        def actualPrintedSchema = new SchemaPrinter().print(actualSchema)

        then:
        printedSchema == actualPrintedSchema

        actualPrintedSchema == '''type OutputType {
  text: String
}

type Query {
  outputField(inputArg: InputType = {age : 666, name : "nameViaArg"}, inputBoolean: Boolean = true, inputInt: Int = 1, inputString: String = "viaArgString"): OutputType
}

input ComplexType {
  boolean: Boolean
  int: Int
  string: String
}

input InputType {
  age: Int = -1
  complex: ComplexType = {boolean : true, int : 666, string : "string"}
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
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(document)

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

}

