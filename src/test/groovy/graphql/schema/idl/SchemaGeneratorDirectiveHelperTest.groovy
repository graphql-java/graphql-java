package graphql.schema.idl

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.language.ObjectTypeDefinition
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.DataFetcher
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLUnionType
import readme.DirectivesExamples
import spock.lang.Specification

import java.time.LocalDateTime

import static graphql.TestUtil.schema
import static graphql.schema.DataFetcherFactories.wrapDataFetcher

class SchemaGeneratorDirectiveHelperTest extends Specification {

    def customScalarType = new GraphQLScalarType("ScalarType", "", new Coercing() {
        @Override
        Object serialize(Object input) throws CoercingSerializeException {
            return input
        }

        @Override
        Object parseValue(Object input) throws CoercingParseValueException {
            return input
        }

        @Override
        Object parseLiteral(Object input) throws CoercingParseLiteralException {
            return input
        }
    })


    def "will trace down into each directive callback"() {

        def spec = '''
            type Query {
                f : ObjectType
                s : ScalarType
            }

            type ObjectType @objectDirective(target : "ObjectType") {
                field1 : String @fieldDirective(target : "field1")
                field2 : String @fieldDirective(target : "field2")
                field3(argument1 : String @argumentDirective(target : "argument1") argument2 : String @argumentDirective(target : "argument2")) : Int   
            }
            
            interface InterfaceType @interfaceDirective(target : "InterfaceType") {
                interfaceField1 : String @fieldDirective(target : "interfaceField1")
                interfaceField2 : String @fieldDirective(target : "interfaceField2")
            }
            
            type Foo {
                foo : Int
            }
            
            type Bar {
                bar :Int
            }
            
            union UnionType @unionDirective(target : "UnionType")  = Foo | Bar
            
            input InputType @inputDirective(target : "InputType") {
                inputField1 : String @inputFieldDirective(target : "inputField1")
                inputField2 : String @inputFieldDirective(target : "inputField2")
            }
            
            enum EnumType @enumDirective(target:"EnumType") {
                enumVal1 @enumValueDirective(target : "enumVal1")
                enumVal2 @enumValueDirective(target : "enumVal2")
            }
            
            scalar ScalarType @scalarDirective(target:"ScalarType")
            
        '''

        def targetList = []

        def schemaDirectiveWiring = new SchemaDirectiveWiring() {

            def assertDirectiveTarget(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment, String name) {
                targetList.add(name)
                GraphQLDirective directive = environment.getDirective()
                String target = directive.getArgument("target").getValue()
                assert name == target, " The target $target is not equal to the object name $name"
                return environment.getElement()
            }

            @Override
            GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLArgument onArgument(SchemaDirectiveWiringEnvironment<GraphQLArgument> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLObjectType onObject(SchemaDirectiveWiringEnvironment<GraphQLObjectType> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLInterfaceType onInterface(SchemaDirectiveWiringEnvironment<GraphQLInterfaceType> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLUnionType onUnion(SchemaDirectiveWiringEnvironment<GraphQLUnionType> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLEnumType onEnum(SchemaDirectiveWiringEnvironment<GraphQLEnumType> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLEnumValueDefinition onEnumValue(SchemaDirectiveWiringEnvironment<GraphQLEnumValueDefinition> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLScalarType onScalar(SchemaDirectiveWiringEnvironment<GraphQLScalarType> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLInputObjectType onInputObjectType(SchemaDirectiveWiringEnvironment<GraphQLInputObjectType> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }

            @Override
            GraphQLInputObjectField onInputObjectField(SchemaDirectiveWiringEnvironment<GraphQLInputObjectField> environment) {
                String name = environment.getElement().getName()
                return assertDirectiveTarget(environment, name)
            }
        }
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("fieldDirective", schemaDirectiveWiring)
                .directive("argumentDirective", schemaDirectiveWiring)
                .directive("objectDirective", schemaDirectiveWiring)
                .directive("interfaceDirective", schemaDirectiveWiring)
                .directive("unionDirective", schemaDirectiveWiring)
                .directive("inputDirective", schemaDirectiveWiring)
                .directive("inputFieldDirective", schemaDirectiveWiring)
                .directive("enumDirective", schemaDirectiveWiring)
                .directive("enumValueDirective", schemaDirectiveWiring)
                .directive("scalarDirective", schemaDirectiveWiring)
                .scalar(customScalarType)
                .wiringFactory(new MockedWiringFactory())
                .build()

        when:
        def schema = schema(spec, runtimeWiring)

        then:
        schema != null
        targetList.contains("ObjectType")
        targetList.contains("field1")
        targetList.contains("field2")

        targetList.contains("InterfaceType")
        targetList.contains("interfaceField1")
        targetList.contains("interfaceField2")

        targetList.contains("UnionType")

        targetList.contains("InputType")
        targetList.contains("inputField1")
        targetList.contains("inputField2")

        targetList.contains("EnumType")
        targetList.contains("enumVal1")
        targetList.contains("enumVal2")

        targetList.contains("ScalarType")
    }

    def "can modify the existing behaviour"() {
        def spec = '''
            type Query {
                lowerCaseValue : String @uppercase
                upperCaseValue : String @lowercase
                echoField1 : String @echoFieldName
                echoField2 : String @echoFieldName @lowercase
                echoField3 : String @echoFieldName @mixedcase
                
                #
                # directives are applied in order hence this will be upper, then lower, then mixed then reversed
                #
                echoField4 : String @echoFieldName @lowercase @uppercase @mixedcase @reverse
            }
        '''

        //
        // This will modify the values returned so that they become different
        // depending on the @directive used
        //
        def casingDirectiveWiring = new SchemaDirectiveWiring() {
            @Override
            GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> directiveEnv) {
                GraphQLFieldDefinition field = directiveEnv.getElement()
                def newFetcher = wrapDataFetcher(field.getDataFetcher(), { dfEnv, value ->
                    def directiveName = directiveEnv.directive.name
                    if (directiveName == "uppercase") {
                        return String.valueOf(value).toUpperCase()
                    } else if (directiveName == "lowercase") {
                        return String.valueOf(value).toLowerCase()
                    } else if (directiveName == "mixedcase") {
                        return toMixedCase(String.valueOf(value))
                    } else if (directiveName == "reverse") {
                        return String.valueOf(value).reverse()
                    }
                })
                field = field.transform({ builder -> builder.dataFetcher(newFetcher) })
                return field
            }

            static String toMixedCase(String s) {
                def out = ""
                s.eachWithIndex { String ch, int i ->
                    out += (i % 2 == 0) ? ch.toUpperCase() : ch.toLowerCase()
                }
                out
            }
        }

        def echoFieldNameWiring = new SchemaDirectiveWiring() {
            @Override
            GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> env) {
                def field = env.getElement()
                def fieldName = field.getName()
                DataFetcher echoDF = { dfEnv ->
                    return fieldName
                 }
                GraphQLFieldDefinition newField = field.transform({ builder -> builder.dataFetcher(echoDF) })
                return newField
            }
        }

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("uppercase", casingDirectiveWiring)
                .directive("lowercase", casingDirectiveWiring)
                .directive("reverse", casingDirectiveWiring)
                .directive("mixedcase", casingDirectiveWiring)
                .directive("echoFieldName", echoFieldNameWiring)
                .build()

        def schema = schema(spec, runtimeWiring)
        def graphQL = GraphQL.newGraphQL(schema).build()
        def input = ExecutionInput.newExecutionInput()
                .root(
                [
                        lowerCaseValue: "lowercasevalue",
                        upperCaseValue: "UPPERCASEVALUE",
                ])
                .query("""
                   query {
                    lowerCaseValue
                    upperCaseValue
                    echoField1
                    echoField2
                    echoField3
                    echoField4
                   }
                """)
                .build()

        when:
        def executionResult = graphQL.execute(input)

        then:
        executionResult.errors.isEmpty()
        executionResult.data == [
                lowerCaseValue: "LOWERCASEVALUE",
                upperCaseValue: "uppercasevalue",
                echoField1    : "echoField1",
                echoField2    : "echofield2",
                echoField3    : "EcHoFiElD3",
                echoField4    : "4DlEiFoHcE",
        ]
    }

    def "ensure the readme examples work"() {

        def spec = '''
            type Query {
                dateField : String @dateFormat
            }
        '''

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("dateFormat", new DirectivesExamples.DateFormatting())
                .build()

        def schema = schema(spec, runtimeWiring)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def day = LocalDateTime.of(1969, 10, 8, 0, 0)
        when:
        def executionInput = ExecutionInput.newExecutionInput().root([dateField: day])
                .query(''' 
                    query {
                        default: dateField
                        usa: dateField(format : "MM-dd-YYYY")
                        yearFirst: dateField(format : "YYYY, MMM dd")
                    }
                ''')
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        executionResult.errors.isEmpty()
        executionResult.data['default'] == '08-10-1969'
        executionResult.data['usa'] == '10-08-1969'
        executionResult.data['yearFirst'] == '1969, Oct 08'
    }

    def "can state-fully track wrapped elements"() {
        def spec = '''
            type Query {
                secret : Secret
                nonSecret : NonSecret
            }
            
            
            type Secret @secret {
                identity : String @secret
                age : Int
            }
            
            type NonSecret {
                identity : String
                age : Int
            }
        '''

        def directiveWiring = new SchemaDirectiveWiring() {
            @Override
            GraphQLObjectType onObject(SchemaDirectiveWiringEnvironment<GraphQLObjectType> environment) {
                def objectType = environment.getElement()

                def definitions = objectType.getFieldDefinitions()
                def contextMap = environment.getBuildContext()

                definitions = definitions.collect { fld -> wrapField(fld, objectType.getName(), contextMap) }

                return objectType.transform({ builder -> builder.clearFields().fields(definitions) })
            }

            @Override
            GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
                GraphQLFieldDefinition element = environment.getElement()
                def tree = environment.getNodeParentTree()
                ObjectTypeDefinition objectTypeDef = tree.parentInfo.get().node
                def contextMap = environment.getBuildContext()

                return wrapField(element, objectTypeDef.getName(), contextMap)
            }

            private GraphQLFieldDefinition wrapField(GraphQLFieldDefinition field, String objectTypeName, Map<String, Object> contextMap) {
                def originalFetcher = field.getDataFetcher()

                String key = mkFieldKey(objectTypeName, field.getName())

                // are we already wrapped
                if (contextMap.containsKey(key)) {
                    return field
                }
                contextMap.put(key, true)

                DataFetcher wrapper = { dfEnv ->
                    def flag = dfEnv.getContext()['protectSecrets']
                    if (flag == null || flag == false) {
                        return originalFetcher.get(dfEnv)
                    }
                    return null
                }
                return field.transform({ builder -> builder.dataFetcher(wrapper) })
            }

            String mkFieldKey(String objectName, String fieldName) {
                return "secret." + objectName + "." + fieldName
            }
        }

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .directive("secret", directiveWiring)
                .build()

        def schema = schema(spec, runtimeWiring)
        def graphQL = GraphQL.newGraphQL(schema).build()

        String query = ''' 
            query {
                secret {
                    identity
                    age
                }
                nonSecret {
                    identity
                    age
                }
            }
        '''
        def root = [secret   : [age: 42, identity: "BruceWayne"],
                    nonSecret: [age: 42, identity: "BruceWayne"]]
        when:
        def executionInput = ExecutionInput.newExecutionInput()
                .root(root)
                .query(query)
                .context([protectSecrets: true])
                .build()

        def executionResult = graphQL.execute(executionInput)

        then:
        executionResult.errors.isEmpty()
        executionResult.data['secret']['identity'] == null
        executionResult.data['secret']['age'] == null
        executionResult.data['nonSecret']['identity'] == "BruceWayne"
        executionResult.data['nonSecret']['age'] == 42

        when:
        executionInput = ExecutionInput.newExecutionInput()
                .root(root)
                .query(query)
                .context([protectSecrets: false])
                .build()

        executionResult = graphQL.execute(executionInput)

        then:
        executionResult.errors.isEmpty()
        executionResult.data['secret']['identity'] == "BruceWayne"
        executionResult.data['secret']['age'] == 42
        executionResult.data['nonSecret']['identity'] == "BruceWayne"
        executionResult.data['nonSecret']['age'] == 42
    }

}
