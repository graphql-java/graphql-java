package graphql.introspection

import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLNamedType
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification

import static graphql.GraphQL.newGraphQL

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

    @Issue("https://github.com/graphql-java/graphql-java/issues/2702")
    def "Introspection#__DirectiveLocation(GraphQLEnumType) `name` should match `value`'s DirectiveLocation#name() #2702"() {
        given:
        def directiveLocationValues = Introspection.__DirectiveLocation.values

        expect:
        directiveLocationValues.each {
            def value = it.value
            assert value instanceof Introspection.DirectiveLocation
            assert it.name == (value as Introspection.DirectiveLocation).name()
        }
    }

    @See("https://spec.graphql.org/October2021/#sec-Schema-Introspection.Schema-Introspection-Schema")
    def "Introspection#__DirectiveLocation(GraphQLEnumType) should have 19 distinct values"() {
        given:
        def directiveLocationValues = Introspection.__DirectiveLocation.values
        def numValues = 19

        expect:
        directiveLocationValues.size() == numValues
        directiveLocationValues.unique(false).size() == numValues
    }

    def "Introspection#__DirectiveLocation(GraphQLEnumType) should contain all Introspection.DirectiveLocation"() {
        given:
        def directiveLocationValues = new ArrayList<>(Introspection.__DirectiveLocation.values)
        def possibleLocations = new ArrayList<>(Introspection.DirectiveLocation.values().toList()).iterator()

        expect:
        while (possibleLocations.hasNext()) {
            def nextPossibleLocation = possibleLocations.next()
            assert directiveLocationValues.retainAll { (it.value != nextPossibleLocation) }
        }
        assert directiveLocationValues.isEmpty()
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

            directive @someDirective(
                deprecatedArg : String @deprecated
                notDeprecatedArg : String
            ) on FIELD 

            type Query {
               namedField(arg : InputType @deprecated ) : Enum @deprecated
               notDeprecated(arg : InputType) : Enum
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
        def namedField = (queryType['fields'] as List).find({ it["name"] == "namedField" })
        namedField["isDeprecated"]

        def notDeprecatedField = (queryType['fields'] as List).find({ it["name"] == "notDeprecated" })
        !notDeprecatedField["isDeprecated"]
        notDeprecatedField["deprecationReason"] == null

        def enumType = types.find { it['name'] == 'Enum' }
        def red = enumType["enumValues"].find({ it["name"] == "RED" })
        red["isDeprecated"]
        red["deprecationReason"] == "No longer supported"

        def inputType = types.find { it['name'] == 'InputType' }
        def inputField = inputType["inputFields"].find({ it["name"] == "inputField" })
        inputField["isDeprecated"]
        inputField["deprecationReason"] == "No longer supported"

        def argument = (namedField["args"] as List).find({ it["name"] == "arg" })
        argument["isDeprecated"]
        argument["deprecationReason"] == "No longer supported"

        def argument2 = (notDeprecatedField["args"] as List).find({ it["name"] == "arg" })
        !argument2["isDeprecated"]
        argument2["deprecationReason"] == null


        def directives = executionResult.data['__schema']['directives'] as List
        def directive = directives.find { it['name'] == "someDirective" }

        def directiveArgs = directive["args"] as List
        directiveArgs.collect({ it["name"] }).sort() == ["deprecatedArg", "notDeprecatedArg"]

        def dArgument = directiveArgs.find({ it["name"] == "deprecatedArg" })
        dArgument["isDeprecated"]
        dArgument["deprecationReason"] == "No longer supported"

    }

    def "can filter out deprecated things in introspection"() {

        def spec = '''

            directive @someDirective(
                deprecatedArg : String @deprecated
                notDeprecatedArg : String
            ) on FIELD 

            type Query {
               namedField(arg : InputType @deprecated,  notDeprecatedArg : InputType ) : Enum @deprecated
               notDeprecated(arg : InputType @deprecated,  notDeprecatedArg : InputType) : Enum
            }
            enum Enum {
                RED @deprecated
                BLUE
            }
            input InputType {
                inputField : String @deprecated
                notDeprecatedInputField : String 
            }
        '''

        def graphQL = TestUtil.graphQL(spec).build()

        when: "we dont include deprecated things"
        def introspectionQueryWithoutDeprecated = IntrospectionQuery.INTROSPECTION_QUERY.replace("includeDeprecated: true", "includeDeprecated: false")

        def executionResult = graphQL.execute(introspectionQueryWithoutDeprecated)

        then:
        executionResult.errors.isEmpty()

        def types = executionResult.data['__schema']['types'] as List
        def queryType = types.find { it['name'] == 'Query' }
        def fields = (queryType['fields'] as List)
        fields.size() == 1

        def notDeprecatedField = fields[0]
        notDeprecatedField["name"] == "notDeprecated"

        def fieldArgs = notDeprecatedField["args"] as List
        fieldArgs.size() == 1
        fieldArgs[0]["name"] == "notDeprecatedArg"

        def enumType = types.find { it['name'] == 'Enum' }
        def enumValues = (enumType['enumValues'] as List)
        enumValues.size() == 1
        enumValues[0]["name"] == "BLUE"

        def inputType = types.find { it['name'] == 'InputType' }
        def inputFields = (inputType['inputFields'] as List)
        inputFields.size() == 1
        inputFields[0]["name"] == "notDeprecatedInputField"

        when: "we DO include deprecated things"
        executionResult = graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY)

        types = executionResult.data['__schema']['types'] as List
        queryType = types.find { it['name'] == 'Query' }
        fields = (queryType['fields'] as List)

        notDeprecatedField = fields.find { it["name"] == "notDeprecated" }
        fieldArgs = notDeprecatedField["args"] as List

        enumType = types.find { it['name'] == 'Enum' }
        enumValues = (enumType['enumValues'] as List)

        inputType = types.find { it['name'] == 'InputType' }
        inputFields = (inputType['inputFields'] as List)

        then:
        executionResult.errors.isEmpty()

        fields.size() == 2

        fieldArgs.size() == 2

        enumValues.size() == 2

        inputFields.size() == 2
    }

    def "can change data fetchers for introspection types"() {
        def sdl = '''
            type Query {
                inA : Int
                inB : Int
                inC : Int
                outA : Int
                outB : Int
                outC : Int
            }
        '''

        def schema = TestUtil.schema(sdl)
        def graphQL = newGraphQL(schema).build()
        def query = '''
            {
                __schema {
                    types {
                        name
                        fields {
                            name
                        }
                    }
                }
            }
        '''

        when:
        def er = graphQL.execute(query)
        then:
        def queryTypeFields = er.data["__schema"]["types"].find({ it["name"] == "Query" })["fields"]
        queryTypeFields == [[name: "inA"], [name: "inB"], [name: "inC"], [name: "outA"], [name: "outB"], [name: "outC"]]

        when:
        DataFetcher introspectionFieldsOfTypeFetcher = { env ->
            GraphQLNamedType type = env.getSource()
            if (type instanceof GraphQLFieldsContainer) {
                def fieldDefs = ((GraphQLFieldsContainer) type).getFieldDefinitions()
                return fieldDefs.stream().filter({ fld -> fld.getName().startsWith("in") }).collect()
            }
        }
        GraphQLCodeRegistry codeRegistry = schema.getCodeRegistry()
        codeRegistry = codeRegistry.transform({
            bld -> bld.dataFetcher(FieldCoordinates.coordinates("__Type", "fields"), introspectionFieldsOfTypeFetcher)
        }
        )
        schema = schema.transform({ bld -> bld.codeRegistry(codeRegistry) })
        graphQL = newGraphQL(schema).build()
        er = graphQL.execute(query)
        queryTypeFields = er.data["__schema"]["types"].find({ it["name"] == "Query" })["fields"]

        then:
        queryTypeFields == [[name: "inA"], [name: "inB"], [name: "inC"]]
    }
}
