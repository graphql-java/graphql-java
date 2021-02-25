package graphql.introspection

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.diff.SchemaDiff
import graphql.schema.idl.SchemaPrinter
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

        println new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeIntrospectionTypes(true)).print(schema)

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
        println TestUtil.prettyPrint(er)

        def helloType = er.data["__schema"]["types"].find({ type -> (type["name"] == "Hello") })
        helloType["extensions"]["directives"] == [[name: "example"]]

        def worldField = helloType["fields"].find({ type -> (type["name"] == "world") })
        worldField["extensions"]["directives"] == [[name: "deprecated"]]
    }
}
