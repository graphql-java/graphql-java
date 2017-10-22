package graphql

import spock.lang.Specification

class RelaySchemaTest extends Specification {

    def "Validate Relay Node schema"() {

        given:
        def query = """{
                      __schema {
                        queryType {
                          fields {
                            name
                            type {
                              name
                              kind
                            }
                            args {
                              name
                              type {
                                kind
                                ofType {
                                  name
                                  kind
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    """
        when:
        def result = GraphQL.newGraphQL(RelaySchema.Schema).build().execute(query)

        then:
        def nodeField = result.data["__schema"]["queryType"]["fields"][0]
        nodeField == [name: "node", type: [name: "Node", kind: "INTERFACE"], args: [[name: "id", type: [kind: "NON_NULL", ofType: [name: "ID", kind: "SCALAR"]]]]]
    }


    def "Validate Relay StuffConnection schema"() {

        given:
        def query = """{
                          __type(name: "StuffConnection") {
                            fields {
                              name
                              type {
                                name
                                kind
                                ofType {
                                  name
                                  kind
                                }
                              }
                            }
                          }
                        }"""
        when:
        def result = GraphQL.newGraphQL(RelaySchema.Schema).build().execute(query)

        then:
        def fields = result.data["__type"]["fields"]
        fields == [[name: "edges", type: [name: null, kind: "LIST", ofType: [name: "StuffEdge", kind: "OBJECT"]]], [name: "pageInfo", type: [name: null, kind: "NON_NULL", ofType: [name: "PageInfo", kind: "OBJECT"]]]]
    }

    def "Validate Relay StuffEdge schema"() {

        given:
        def query = """{
                          __type(name: "StuffEdge") {
                            fields {
                              name
                              type {
                                name
                                kind
                                ofType {
                                  name
                                  kind
                                }
                              }
                            }
                          }
                        }
                    """
        when:
        def result = GraphQL.newGraphQL(RelaySchema.Schema).build().execute(query)

        then:
        def fields = result.data["__type"]["fields"]
        fields == [[name: "node", type: [name: "Stuff", kind: "OBJECT", ofType: null]], [name: "cursor", type: [name: null, kind: "NON_NULL", ofType: [name: "String", kind: "SCALAR"]]]]
    }

}
