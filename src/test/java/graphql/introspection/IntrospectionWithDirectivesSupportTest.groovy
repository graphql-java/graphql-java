package graphql.introspection

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

class IntrospectionWithDirectivesSupportTest extends Specification {

    def "can find directives in introspection"() {
        def sdl = '''
            type Query {
                hello : Hello
            }
            
            type Hello {
                world : String @deprecated
            }
        '''

        def schema = TestUtil.schema(sdl)
        def printOptions = SchemaPrinter.Options.defaultOptions().includeIntrospectionTypes(true)
        println("Before========")
        println(new SchemaPrinter(printOptions).print(schema))

        schema = new IntrospectionWithDirectivesSupport().apply(schema)
        println("After=========")
        println(new SchemaPrinter(printOptions).print(schema))


        def graphql = GraphQL.newGraphQL(schema).build()

        def query = '''
        {
            __schema {
                types {
                    name
                    fields {
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
    }
}
