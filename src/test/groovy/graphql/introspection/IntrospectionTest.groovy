package graphql.introspection

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.SchemaTransformer
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import graphql.util.TreeTransformerUtil
import spock.lang.Specification

class IntrospectionTest extends Specification {

    def "bug 1186 - introspection depth check"() {
        def spec = '''
            type Query {
                geo : GeoPolygon 
            }
                
            type GeoPolygon {
                 coordinates: [[[[[Float]]]]]!
            }
        '''

        def graphQL = TestUtil.graphQL(spec).build()
        when:
        def executionResult = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)
        then:
        executionResult.errors.isEmpty()

        def types = executionResult.data['__schema']['types'] as List
        def geoPolygonType = types.find { it['name'] == 'GeoPolygon' }
        def coordinatesField = (geoPolygonType['fields'] as List)[0]
        def fieldType = coordinatesField['type']
        // should show up to 7 levels deep like GraphIQL does
        fieldType == [
                kind  : 'NON_NULL',
                name  : null,
                ofType: [
                        kind  : 'LIST',
                        name  : null,
                        ofType: [
                                kind  : 'LIST',
                                name  : null,
                                ofType: [
                                        kind  : 'LIST',
                                        name  : null,
                                        ofType: [
                                                kind  : 'LIST',
                                                name  : null,
                                                ofType: [
                                                        kind  : 'LIST',
                                                        name  : null,
                                                        ofType: [
                                                                kind  : 'SCALAR',
                                                                name  : 'Float',
                                                                ofType: null]
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]
    }

    def "can query directives on types via graphql java specific api"() {
        given:
        def spec = '''
            directive @myDirective(myArg: String) on FIELD_DEFINITION | OBJECT
           
            type Query @myDirective(myArg: "on query") {
                hello : String @myDirective(myArg: "on field")
            }
        '''
        def schema = TestUtil.schema(spec)
        def schemaTransformer = new SchemaTransformer()
        def schemaWithExposedDirective = schemaTransformer.transform(schema, new GraphQLTypeVisitorStub() {

            @Override
            TraversalControl visitGraphQLDirective(GraphQLDirective graphQLDirective, TraverserContext<GraphQLSchemaElement> context) {
                def newDirective = graphQLDirective.transform({ it.exposeViaIntrospection(true) })
                return TreeTransformerUtil.changeNode(context, newDirective)
            }
        })

        def graphql = GraphQL.newGraphQL(schemaWithExposedDirective).build()

        def query = """
        { __schema {
                types {
                ...FullType
                }
            }
       }
      fragment FullType on __Type  {
        directives_GJ_API {
            name
            args {
               ...InputValue 
            }
        } 
        kind
        name
        description
        fields(includeDeprecated: true) {
          name
          description
          args {
            ...InputValue
          }
          type {
            ...TypeRef
          }
          isDeprecated
          deprecationReason
            directives_GJ_API {
                name
                args {
                   ...InputValue 
                }
            } 
        }
        inputFields {
          ...InputValue
        }
        interfaces {
          ...TypeRef
        }
        enumValues(includeDeprecated: true) {
          name
          description
          isDeprecated
          deprecationReason
        }
        possibleTypes {
          ...TypeRef
        }
      }
    
      fragment InputValue on __InputValue {
        name
        description
        type { ...TypeRef }
        defaultValue
        value_GJ_API
      }
            
               fragment TypeRef on __Type {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                        ofType {
                          kind
                          name
                          ofType {
                            kind
                            name
                            ofType {
                              kind
                              name
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }

        """
        when:
        def result = graphql.execute(query)
        def types = result.data["__schema"]["types"]
        def queryType = types.find { it.name == "Query" }
        def queryDirective = queryType["directives_GJ_API"][0]
        def helloField = queryType["fields"][0]
        def helloFieldDirective = helloField["directives_GJ_API"][0]

        then:
        queryDirective.name == "myDirective"
        queryDirective.args.find { it.name == "myArg" }["value_GJ_API"] == '"on query"'

        helloFieldDirective.name == "myDirective"
        helloFieldDirective.args.find { it.name == "myArg" }["value_GJ_API"] == '"on field"'

    }
}
