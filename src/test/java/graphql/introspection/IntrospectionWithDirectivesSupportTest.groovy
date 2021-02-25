package graphql.introspection

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.idl.SchemaPrinter
import groovy.json.JsonOutput
import spock.lang.Specification

class IntrospectionWithDirectivesSupportTest extends Specification {

    def "can find directives in introspection"() {
        def sdl = '''
            directive @example on OBJECT
            
            
            type Query @example {
                hello : Hello @deprecated
            }
            
            type Hello @example {
                world : String @deprecated
            }
        '''

        def schema = TestUtil.schema(sdl)
        schema = new IntrospectionWithDirectivesSupport().apply(schema)

        def graphql = GraphQL.newGraphQL(schema).build()

        def query = '''
        {
            __schema {
                types {
                    name
                     extensions {
                            directives {
                               name
                           }
                       }
                    fields(includeDeprecated:true) {
                        name
                        extensions {
                            directives {
                               name
                           }
                       }
                    }
                }
            }
        }
        '''

        when:

        def er = graphql.execute(query)
        then:
        er.errors.isEmpty()
        println JsonOutput.prettyPrint(JsonOutput.toJson(er.data))

        def data = er.data["__schema"]["types"].find({ type -> type["name"].equals("Hello") })
        data != null
    }
}
