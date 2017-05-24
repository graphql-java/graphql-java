package graphql.introspection

import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.language.AstPrinter
import graphql.language.ObjectTypeDefinition
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Specification

class IntrospectionResultToSchemaTest extends Specification {

    def "Allow querying the schema with pre-defined full introspection query"() {
        given:
        def query = IntrospectionQuery.INTROSPECTION_QUERY

        when:
        def result = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build().execute(query)
        println result.data
        println JsonOutput.prettyPrint(JsonOutput.toJson(result.data))

        then:
        Map<String, Object> schema = (Map<String, Object>) result.data
        schema.size() == 1
        Map<String, Object> schemaParts = (Map<String, Map>) schema.get("__schema")
        schemaParts.size() == 5
        schemaParts.get('queryType').size() == 1
        schemaParts.get('mutationType') == null
        schemaParts.get('subscriptionType') == null
        schemaParts.get('types').size() == 15
        schemaParts.get('directives').size() == 2
    }

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
                    "description": "If omitted, returns the hero of the whole saga. If provided, returns the hero of that particular episode.",
                    "type": {
                      "kind": "ENUM",
                      "name": "Episode",
                      "ofType": null
                    },
                    "defaultValue": null
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
        println astPrinter.printAst(objectTypeDefinition)

        then:
        true

    }
}

