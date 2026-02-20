package graphql.introspection

import graphql.GraphQL
import graphql.TestUtil
import spock.lang.Specification

class IntrospectionWithDirectivesSupportTest extends Specification {

    def "can find directives in introspection"() {
        def sdl = '''
            directive @example( argName : String = "default") on OBJECT | FIELD_DEFINITION | INPUT_OBJECT | INPUT_FIELD_DEFINITION | SCHEMA
            directive @secret( argName : String = "secret") on OBJECT
            directive @noDefault( arg1 : String, arg2 : String) on OBJECT
            
            schema @example(argName : "onSchema") {
                query : Query
            }
            
            type Query @example(argName : "onQuery") @noDefault(arg1 : "set") {
                hello : Hello @deprecated
            }
            
            type Hello @example @noDefault {
                world : String @deprecated
            }
            
            input InputType {
                inputField : String @example(argName : "onInputField")
            }
        '''

        def schema = TestUtil.schema(sdl)
        schema = new IntrospectionWithDirectivesSupport().apply(schema)
        def graphql = GraphQL.newGraphQL(schema).build()

        def query = '''
        {
            __schema {
                directives {
                    name
                }
                appliedDirectives {
                    name
                    args {
                        name
                        value
                    }                             
                }
                types {
                    name
                    appliedDirectives {
                        name
                        args {
                            name
                            value
                        }                             
                    }
                    fields(includeDeprecated:true) {
                        name
                        appliedDirectives {
                            name
                            args {
                                name
                                value
                            }                             
                        }
                    }
                    inputFields {
                        name
                        appliedDirectives {
                            name
                            args {
                                name
                                value
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

        def schemaType = er.data["__schema"]

        schemaType["directives"] == [
                [name: "include"], [name: "skip"], [name: "defer"], [name: "experimental_disableErrorPropagation"],
                [name: "example"], [name: "secret"], [name: "noDefault"],
                [name: "deprecated"], [name: "specifiedBy"], [name: "oneOf"]
        ]

        schemaType["appliedDirectives"] == [[name: "example", args: [[name: "argName", value: '"onSchema"']]]]

        def queryType = er.data["__schema"]["types"].find({ type -> (type["name"] == "Query") })
        queryType["appliedDirectives"] == [
                [name: "example", args: [[name: "argName", value: '"onQuery"']]],
                [name: "noDefault", args: [[name: "arg1", value: '"set"']]]
        ]

        def helloType = er.data["__schema"]["types"].find({ type -> (type["name"] == "Hello") })
        helloType["appliedDirectives"] == [
                [name: "example", args: [[name: "argName", value: '"default"']]],
                [name: "noDefault", args: []] // always empty list
        ]

        def worldField = helloType["fields"].find({ type -> (type["name"] == "world") })
        worldField["appliedDirectives"] == [[name: 'deprecated', args: [[name: 'reason', value: '"No longer supported"']]]]

        def inputType = er.data["__schema"]["types"].find({ type -> (type["name"] == "InputType") })
        def inputField = inputType["inputFields"].find({ type -> (type["name"] == "inputField") })
        inputField["appliedDirectives"] == [[name: 'example', args: [[name: 'argName', value: '"onInputField"']]]]

    }

    def "can filter the directives returned in introspection"() {
        def sdl = '''
            directive @example( argName : String = "default") on OBJECT
            directive @secret( argName : String = "secret") on OBJECT
            
            type Query {
                hello : Hello
            }
            
            type Hello @example @secret {
                world : String 
            }
        '''

        def schema = TestUtil.schema(sdl)
        def filter = new IntrospectionWithDirectivesSupport.DirectivePredicate() {
            @Override
            boolean isDirectiveIncluded(IntrospectionWithDirectivesSupport.DirectivePredicateEnvironment env) {
                return !env.getDirectiveName().contains("secret")
            }
        }
        schema = new IntrospectionWithDirectivesSupport(filter).apply(schema)

        def graphql = GraphQL.newGraphQL(schema).build()

        def query = '''
        {
            __schema {
                directives {
                    name
                }
                types {
                    name
                    appliedDirectives {
                        name
                        args {
                            name
                            value
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

        def helloType = er.data["__schema"]["types"].find({ type -> (type["name"] == "Hello") })
        helloType["appliedDirectives"] == [[name: "example", args: [[name: "argName", value: '"default"']]]]

        def definedDirectives = er.data["__schema"]["directives"]
        // secret is filter out
        definedDirectives == [[name: "include"], [name: "skip"], [name: "defer"], [name: "experimental_disableErrorPropagation"],
                              [name: "example"], [name: "deprecated"], [name: "specifiedBy"], [name: "oneOf"]
        ]
    }

    def "can set prefixes onto the Applied types"() {
        def sdl = '''
            type Query {
                hello : __Hello
            }
            
            type __Hello {
                world : _Bar 
            }
            
            type _Bar {
                bar  : String
            }
        '''

        def schema = TestUtil.schema(sdl)
        def filter = new IntrospectionWithDirectivesSupport.DirectivePredicate() {
            @Override
            boolean isDirectiveIncluded(IntrospectionWithDirectivesSupport.DirectivePredicateEnvironment env) {
                return !env.getDirective().getName().contains("secret")
            }
        }
        def newSchema = new IntrospectionWithDirectivesSupport(filter, "__x__").apply(schema)
        def graphql = GraphQL.newGraphQL(newSchema).build()

        def query = '''
        {
            __schema {
                types {
                    name
                }
            }
        }
        '''

        when:
        def er = graphql.execute(query)
        then:
        er.errors.isEmpty()
        def types = er.data["__schema"]["types"]

        types.find({ type -> (type["name"] == "__x__AppliedDirective") }) != null
        types.find({ type -> (type["name"] == "__x__DirectiveArgument") }) != null

        when:

        newSchema = new IntrospectionWithDirectivesSupport(filter, "__").apply(schema)
        graphql = GraphQL.newGraphQL(newSchema).build()
        er = graphql.execute(query)

        then:
        er.errors.isEmpty()
        def types2 = er.data["__schema"]["types"]

        types2.find({ type -> (type["name"] == "__AppliedDirective") }) != null
        types2.find({ type -> (type["name"] == "__DirectiveArgument") }) != null
    }
}
