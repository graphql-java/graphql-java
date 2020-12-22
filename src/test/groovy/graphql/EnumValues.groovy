package graphql

import graphql.schema.idl.NaturalEnumValuesProvider
import spock.lang.Specification

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class EnumValues extends Specification {

    static enum SomeEnum {
        TWO(2),
        THREE(3)
        public final int number

        SomeEnum(int number) {
            this.number = number
        }
    }

    def spec = '''
            enum SomeEnum {
                TWO
                THREE
            }
            
            input Input {
                e: SomeEnum!
            }
            
            type Query {
                field(e: SomeEnum = TWO) : Int!
                wrappedArgField(e: [SomeEnum!]! = [TWO, THREE]) : Int!
                inputField(i: Input = {e: THREE}) : Int!
            }
            '''

    def typeRuntimeWiring = newTypeWiring('SomeEnum').enumValues(new NaturalEnumValuesProvider(SomeEnum)).build()
    def queryRuntimeWriting = newTypeWiring('Query')
            .dataFetcher(
                    'field',
                    { env ->
                        def arg = env.getArgument("e")
                        // Be careful! In groovy `arg as SomeEnum` will convert a string to an enum, and the point of this test
                        // is to show that this isn't needed.
                        assert arg instanceof SomeEnum
                        arg.number
                    })
            .dataFetcher(
                    'wrappedArgField',
                    { env ->
                        def arg = env.getArgument("e")
                        // Be careful! In groovy `arg as SomeEnum` will convert a string to an enum, and the point of this test
                        // is to show that this isn't needed.
                        (arg as List).collect { e ->
                            // Be careful! In groovy `e as SomeEnum` will convert a string to an enum, and the point of this test
                            // is to show that this isn't needed.
                            assert e instanceof SomeEnum
                            e.number
                        }.sum()
                    })
            .dataFetcher(
                    'inputField',
                    { env ->
                        Map arg = env.getArgument("i")
                        def e = arg.get("e")
                        // Be careful! In groovy `arg as SomeEnum` will convert a string to an enum, and the point of this test
                        // is to show that this isn't needed.
                        assert e instanceof SomeEnum
                        e.number
                    })
    def runtimeWiring = newRuntimeWiring().type(typeRuntimeWiring).type(queryRuntimeWriting).build()
    def graphql = TestUtil.graphQL(spec, runtimeWiring).build()

    def "explicit enum values go through EnumValuesProvider"() {
        when:
        def result = graphql.execute('''
                {
                  field(e: THREE)
                }   
            ''')

        then:
        result.errors.isEmpty()
        result.data == [field: 3]
    }

    def "default enum values go through EnumValuesProvider"() {
        when:
        def result = graphql.execute('''
                {
                  field
                }   
            ''')

        then:
        result.errors.isEmpty()
        result.data == [field: 2]
    }

    def "wrapped explicit enum values go through EnumValuesProvider"() {
        when:
        def result = graphql.execute('''
                {
                  wrappedArgField(e: [TWO, TWO, THREE])
                }   
            ''')

        then:
        result.errors.isEmpty()
        result.data == [wrappedArgField: 7]
    }

    def "wrapped default enum values go through EnumValuesProvider"() {
        when:
        def result = graphql.execute('''
                {
                  wrappedArgField
                }   
            ''')

        then:
        result.errors.isEmpty()
        result.data == [wrappedArgField: 5]
    }

    def "explicit enum values in input objects go through EnumValuesProvider"() {
        when:
        def result = graphql.execute('''
                {
                  inputField(i: {e: TWO})
                }   
            ''')

        then:
        result.errors.isEmpty()
        result.data == [inputField: 2]
    }

    def "default enum values in input objects go through EnumValuesProvider"() {
        when:
        def result = graphql.execute('''
                {
                  inputField
                }   
            ''')

        then:
        result.errors.isEmpty()
        result.data == [inputField: 3]
    }
}
