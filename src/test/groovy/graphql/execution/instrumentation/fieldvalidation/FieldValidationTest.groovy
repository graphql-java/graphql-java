package graphql.execution.instrumentation.fieldvalidation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.TestUtil
import graphql.execution.AbortExecutionException
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.Execution
import graphql.execution.ExecutionId
import graphql.execution.ExecutionPath
import graphql.execution.instrumentation.NoOpInstrumentation
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class FieldValidationTest extends Specification {
    def idl = """
            type Information {
                informationString(fmt1 : String = "defaultFmt1", fmt2 : String = "defaultFmt2" ) : String
                informationInt : Int
                informationLink : Information
            }
            
            input InputData {
                string: String
                int: Int
            }
            
            type Query {
                field1(inputData : InputData, id : ID) : Information
                field2(stringArg : String) : String
                field3(intArg : Int) : Int
                noArgField : Int
            }
        """

    def schema = TestUtil.schema(idl)

    def query = '''
        query VarTest($stringVar : String, $intVar : Int, $inputDataVar : InputData, $idVar : ID, $fmt1Var : String) {
            field1(inputData : $inputDataVar, id : $idVar) {
                informationString 
                informationLink {
                    informationString(fmt1 : "inlineFmt1")
                        informationLink {
                        informationString(fmt1 : $fmt1Var)
                    }
                }
            }
            field2(stringArg : $stringVar)
            field3(intArg : $intVar)
        }
        '''


    def "test_field_args_can_be_validated"() {
        given:

        def variables = [
                stringVar   : "stringValue",
                intVar      : 666,
                inputDataVar: [string: "string", int: 0],
                idVar       : "ID12345",
                fmt1Var     : "fmt1Value"
        ]

        def validator = new FieldValidation() {
            @Override
            List<GraphQLError> validateField(FieldValidationEnvironment validationEnvironment) {
                Map<String, Object> values

                def fieldArguments = validationEnvironment.getFields()

                FieldAndArguments field1Args = fieldArguments.get(ExecutionPath.fromString("/field1"))

                values = field1Args.getFieldArgumentValues()

                assert values['inputData'] == [string: "string", int: 0]
                assert values['id'] == "ID12345"
                assert !values.containsKey('stringArg')
                assert !values.containsKey('intArg')


                values = fieldArguments.get(ExecutionPath.fromString("/field2"))
                        .getFieldArgumentValues()

                assert values['stringArg'] == "stringValue"

                values = fieldArguments.get(ExecutionPath.fromString("/field3"))
                        .getFieldArgumentValues()

                assert values['intArg'] == 666

                assert !fieldArguments.containsKey(ExecutionPath.fromString("/noArgField"))

                values = fieldArguments.get(ExecutionPath.fromString("/field1/informationLink/informationString"))
                        .getFieldArgumentValues()

                assert values['fmt1'] == "inlineFmt1" // inlined from query
                assert values['fmt2'] == "defaultFmt2" // defaulted from schema

                def linkLink = fieldArguments.get(ExecutionPath.fromString("/field1/informationLink/informationLink/informationString"))
                values = linkLink
                        .getFieldArgumentValues()

                assert values['fmt1'] == "fmt1Value" // from variables
                assert values['fmt2'] == "defaultFmt2" // defaulted from schema

                // parent check
                assert linkLink.getParentFieldAndArguments().getPath().toString() == "/field1/informationLink/informationLink"
                assert linkLink.getParentFieldAndArguments().getParentFieldAndArguments().getPath().toString() == "/field1/informationLink"
                assert linkLink.getParentFieldAndArguments().getParentFieldAndArguments().getParentFieldAndArguments().getPath().toString() == "/field1"


                return [
                        validationEnvironment.mkError("Made up some error here", field1Args)
                ]
            }
        }

        when:

        setupExecution(validator, query, variables)

        then:
        def abortExecutionException = thrown(AbortExecutionException)
        def errors = abortExecutionException.getUnderlyingErrors()
        errors.size() == 1
        errors[0].getMessage() == "Made up some error here"
        errors[0].getLocations().size() == 1
        errors[0].getPath() == ["field1"]
    }

    def "test SimpleFieldAndArgumentsValidator"() {
        given:

        def variables = [
                stringVar   : "stringValue",
                intVar      : 666,
                inputDataVar: [string: "string", int: 0],
                idVar       : "ID12345",
                fmt1Var     : "fmt1Value"
        ]

        SimpleFieldValidation validator = new SimpleFieldValidation()
                .addRule(ExecutionPath.fromString("/field1"),
                { fieldAndArguments, env -> err("Not happy Jan!", env, fieldAndArguments) })
                .addRule(ExecutionPath.fromString("/field1/informationLink/informationLink/informationString"),
                { fieldAndArguments, env -> err("Also not happy Jan!", env, fieldAndArguments) })
                .addRule(ExecutionPath.fromString("/does/not/exist"),
                { fieldAndArguments, env -> err("Wont happen", env, fieldAndArguments) })


        when:
        setupExecution(validator, query, variables)

        then:
        def abortExecutionException = thrown(AbortExecutionException)
        def errors = abortExecutionException.getUnderlyingErrors()

        errors.size() == 2
        errors[0].getMessage() == "Not happy Jan!"
        errors[0].getLocations().size() == 1
        errors[0].getPath() == ["field1"]

        errors[1].getMessage() == "Also not happy Jan!"
    }


    def "test graphql from end to end"() {

        def validationInstrumentation = new FieldValidationInstrumentation(new FieldValidation() {
            @Override
            List<GraphQLError> validateField(FieldValidationEnvironment env) {
                return [
                        env.mkError("I was called"),
                        env.mkError("I made 2 errors")
                ]
            }
        })

        GraphQL graphql = GraphQL.newGraphQL(schema).instrumentation(validationInstrumentation).build()

        when:
        def result = graphql.execute("{ field2 }")

        then:
        result.getErrors().size() == 2
        result.getErrors()[0].getMessage() == "I was called"
        result.getErrors()[1].getMessage() == "I made 2 errors"
    }


    static GraphQLError err(String msg, FieldValidationEnvironment env, FieldAndArguments fieldAndArguments) {
        env.mkError(msg, fieldAndArguments)
    }


    private CompletableFuture<ExecutionResult> setupExecution(FieldValidation validation, String query, Map<String, Object> variables) {
        def document = TestUtil.parseQuery(query)
        def strategy = new AsyncExecutionStrategy()
        def instrumentation = new FieldValidationInstrumentation(validation)
        def execution = new Execution(strategy, strategy, strategy, instrumentation)

        def executionInput = ExecutionInput.newExecutionInput().query(query).variables(variables).build()
        execution.execute(document, schema, ExecutionId.generate(), executionInput, NoOpInstrumentation.INSTANCE.createState())
    }

}
