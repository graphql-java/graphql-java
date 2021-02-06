package graphql.introspection


import graphql.TestUtil
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

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

    def "schema description can be defined in SDL and queried via introspection"() {
        given:
        def sdl = ''' 
        """
        This is my schema
        """
        schema {
            query: Foo
        }
        
        type Foo {
            foo: String
        }
        
        '''
        def graphql = TestUtil.graphQL(sdl).build()
        when:
        def data = graphql.execute("{__schema { description }}").getData()

        then:
        data == [__schema: [description: "This is my schema"]]

    }

    def "introspection for repeatable directive info"() {
        def spec = '''
            directive @repeatableDirective(arg: String) repeatable on FIELD
             
            type Query {
               namedField: String
            }
        '''

        when:
        def graphQL = TestUtil.graphQL(spec).build()
        def executionResult = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)

        then:
        executionResult.errors.isEmpty()

        def directives = executionResult.data.getAt("__schema").getAt("directives") as List
        def geoPolygonType = directives.find { it['name'] == 'repeatableDirective' }
        geoPolygonType["isRepeatable"] == true
    }

    def "introspection for deprecated support"() {
        def spec = '''
            type Query {
               namedField(arg : InputType @deprecated ) : Enum @deprecated
            }
            enum Enum {
                RED @deprecated
                BLUE
            }
            input InputType {
                inputField : String @deprecated
            }
        '''

        when:
        def graphQL = TestUtil.graphQL(spec).build()
        def executionResult = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)

        then:
        executionResult.errors.isEmpty()

        def types = executionResult.data['__schema']['types'] as List
        def queryType = types.find { it['name'] == 'Query' }
        def namedField = (queryType['fields'] as List)[0]
        namedField["isDeprecated"]
        namedField["deprecationReason"] == "No longer supported"

        def enumType = types.find { it['name'] == 'Enum' }
        def red = enumType["enumValues"].find({ it["name"] == "RED"})
        red["isDeprecated"]
        red["deprecationReason"] == "No longer supported"

        def inputType = types.find { it['name'] == 'InputType' }
        def inputField = inputType["inputFields"].find({ it["name"] == "inputField"})
        inputField["isDeprecated"]
        inputField["deprecationReason"] == "No longer supported"

        def argument = (namedField["args"] as List)[0]
        argument["isDeprecated"]
        argument["deprecationReason"] == "No longer supported"
    }
}
