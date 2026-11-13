package graphql.schema.idl

import graphql.schema.DataFetcher
import graphql.schema.idl.errors.StrictModeWiringException
import spock.lang.Specification

class TypeRuntimeWiringTest extends Specification {

    void setup() {
        TypeRuntimeWiring.setStrictModeJvmWide(true)
    }

    void cleanup() {
        TypeRuntimeWiring.setStrictModeJvmWide(true)
    }

    DataFetcher DF1 = env -> "x"
    DataFetcher DF2 = env -> "y"

    def "strict mode is on by default"() {
        when:
        TypeRuntimeWiring.newTypeWiring("Foo")
                .dataFetcher("foo", DF1)
                .dataFetcher("foo", DF2)
                .build()
        then:
        def e = thrown(StrictModeWiringException)
        e.message == "The field foo already has a data fetcher defined"
    }

    def "strict mode can be turned off"() {
        when:
        def typeRuntimeWiring = TypeRuntimeWiring.newTypeWiring("Foo")
                .strictMode(false)
                .dataFetcher("foo", DF1)
                .dataFetcher("foo", DF2)
                .build()
        then:
        typeRuntimeWiring.getFieldDataFetchers().get("foo") == DF2
    }

    def "strict mode, on by default, works for maps of fields"() {
        when:
        TypeRuntimeWiring.newTypeWiring("Foo")
                .dataFetcher("foo", DF1)
                .dataFetchers(["foo": DF2])
                .build()
        then:
        def e = thrown(StrictModeWiringException)
        e.message == "The field foo already has a data fetcher defined"
    }

    def "strict mode can be turned off and on JVM wide"() {
        when:
        def inStrictMode = TypeRuntimeWiring.getStrictModeJvmWide()
        then:
        inStrictMode

        when:
        TypeRuntimeWiring.setStrictModeJvmWide(false)
        inStrictMode = TypeRuntimeWiring.getStrictModeJvmWide()

        TypeRuntimeWiring.newTypeWiring("Foo")
                .dataFetcher("foo", DF1)
                .dataFetcher("foo", DF2)
                .build()
        then:
        !inStrictMode
        noExceptionThrown()

        when:
        TypeRuntimeWiring.setStrictModeJvmWide(true)
        inStrictMode = TypeRuntimeWiring.getStrictModeJvmWide()

        TypeRuntimeWiring.newTypeWiring("Foo")
                .dataFetcher("foo", DF1)
                .dataFetcher("foo", DF2)
                .build()

        then:
        inStrictMode
        def e = thrown(StrictModeWiringException)
        e.message == "The field foo already has a data fetcher defined"
    }
}
