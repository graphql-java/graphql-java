package graphql.introspection

import graphql.language.*
import groovy.json.JsonSlurper
import spock.lang.Specification

class IntrospectionResultToSchemaTest extends Specification {

    def introspectionResultToSchema = new IntrospectionResultToSchema()

    def "create object"() {
        def input = """ {
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
                    "defaultValue": "bar"
                  }
                ],
                "type": {
                  "kind": "INTERFACE",
                  "name": "Character",
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
      }
      """
        def slurper = new JsonSlurper()
        def parsed = slurper.parseText(input)

        when:
        ObjectTypeDefinition objectTypeDefinition = introspectionResultToSchema.createObject(parsed)
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(objectTypeDefinition)

        then:
        result == """type QueryType {
  hero(
  #comment about episode
  episode: Episode
  foo: String = \"bar\"
  ): Character
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
        def slurper = new JsonSlurper()
        def parsed = slurper.parseText(input)

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
            "isDeprecated": false,
            "deprecationReason": null
          }
        ],
        "possibleTypes": null
      }
      """
        def slurper = new JsonSlurper()
        def parsed = slurper.parseText(input)

        when:
        EnumTypeDefinition interfaceTypeDefinition = introspectionResultToSchema.createEnum(parsed)
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(interfaceTypeDefinition)

        then:
        result == """#One of the films in the Star Wars Trilogy
enum Episode {
  #Released in 1977.
  NEWHOPE
  #Released in 1980.
  EMPIRE
  #Released in 1983.
  JEDI
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
        def slurper = new JsonSlurper()
        def parsed = slurper.parseText(input)

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
        def slurper = new JsonSlurper()
        def parsed = slurper.parseText(input)

        when:
        InputObjectTypeDefinition inputObjectTypeDefinition = introspectionResultToSchema.createInputObject(parsed)
        AstPrinter astPrinter = new AstPrinter()
        def result = astPrinter.printAst(inputObjectTypeDefinition)

        then:
        result == """#input for characters
CharacterInput {
  #first name
  firstName: String
  lastName: String
  family: Boolean
}"""
    }

}

