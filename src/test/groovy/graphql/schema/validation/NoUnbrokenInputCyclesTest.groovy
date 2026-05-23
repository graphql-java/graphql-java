package graphql.schema.validation

import graphql.TestUtil
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLTypeReference.typeRef

class NoUnbrokenInputCyclesTest extends Specification {

    SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

    def "infinitely recursive input type results in error"() {
        given:
        GraphQLInputObjectType PersonInputType = newInputObject()
                .name("Person")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("friend")
                .type(typeRef("Person"))
                .build())
                .build()

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("exists")
                .type(GraphQLBoolean)
                .argument(GraphQLArgument.newArgument()
                        .name("person")
                        .type(PersonInputType))
                .build()

        PersonInputType.getFieldDefinition("friend").replacedType = nonNull(PersonInputType)
        def context = Mock(TraverserContext)
        context.getVarFromParents(SchemaValidationErrorCollector) >> errorCollector
        when:
        new NoUnbrokenInputCycles().visitGraphQLFieldDefinition(field, context)
        then:
        errorCollector.containsValidationError(SchemaValidationErrorType.UnbrokenInputCycle)
    }

    def "input object cycles through a non-null list are allowed"() {
        def sdl = """
            input Example {
                self: [Example!]!
                value: String
            }

            type Query {
                example(example: Example): String
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }

    def "longer input object cycles through a non-null list are allowed"() {
        def sdl = """
            input Foo {
                bar: Bar!
            }

            input Bar {
                baz: Baz!
            }

            input Baz {
                foos: [Foo!]!
            }

            type Query {
                foo(foo: Foo): String
            }
        """

        when:
        def registry = new SchemaParser().parse(sdl)
        new SchemaGenerator().makeExecutableSchema(registry, TestUtil.getMockRuntimeWiring())

        then:
        noExceptionThrown()
    }
}
