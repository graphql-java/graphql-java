package graphql.introspection

import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.idl.SchemaPrinter
import spock.lang.Specification

class IntrospectionWithDirectivesSupportTest extends Specification {

    def printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions().includeIntrospectionTypes(true))

    def "can find directives in introspection"() {
        def sdl = '''
            directive @example( argName : String = "default") on OBJECT
            
            
            type Query @example(argName : "onQuery") {
                hello : Hello @deprecated
            }
            
            type Hello @example {
                world : String @deprecated
            }
        '''

        def schema = TestUtil.schema(sdl)
        schema = new IntrospectionWithDirectivesSupport().apply(schema)

        println printer.print(schema)

        def graphql = GraphQL.newGraphQL(schema).build()

        def query = '''
        {
            __schema {
                types {
                    name
                     extensions {
                        directives {
                            name
                            args {
                                name
                                extensions {
                                    value
                                }  
                            }                             
                        }
                   }
                   fields(includeDeprecated:true) {
                        name
                        extensions {
                            directives {
                                name
                                args {
                                    name
                                    extensions {
                                        value
                                    }  
                                }                             
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

        def queryType = er.data["__schema"]["types"].find({ type -> (type["name"] == "Query") })
        queryType["extensions"]["directives"] == [[name: "example", args: [[name: "argName", extensions: [value: '"onQuery"']]]]]

        def helloType = er.data["__schema"]["types"].find({ type -> (type["name"] == "Hello") })
        helloType["extensions"]["directives"] == [[name: "example", args: [[name: "argName", extensions: [value: '"default"']]]]]

        def worldField = helloType["fields"].find({ type -> (type["name"] == "world") })
        worldField["extensions"]["directives"] == [[name: 'deprecated', args: [[name: 'reason', extensions: [value: '"No longer supported"']]]]
        ]
    }
}
