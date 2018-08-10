package graphql.schema

import graphql.execution.ExecutionContext
import graphql.schema.somepackage.ClassWithDFEMethods
import graphql.schema.somepackage.TestClass
import graphql.schema.somepackage.TwoClassesDown
import spock.lang.Specification

import java.util.function.Function

import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment

class PropertyDataFetcherTest extends Specification {

    def env(obj) {
        newDataFetchingEnvironment()
                .executionContext(Mock(ExecutionContext))
                .source(obj)
                .arguments([argument1: "value1", argument2: "value2"])
                .build()
    }

    class SomeObject {
        String value
    }

    def "null source is always null"() {
        def environment = env(null)
        def fetcher = new PropertyDataFetcher("someProperty")
        expect:
        fetcher.get(environment) == null
    }

    def "function based fetcher works with non null source"() {
        def environment = env(new SomeObject(value: "aValue"))
        Function<Object, String> f = { obj -> obj['value'] }
        def fetcher = PropertyDataFetcher.fetching(f)
        expect:
        fetcher.get(environment) == "aValue"
    }

    def "function based fetcher works with null source"() {
        def environment = env(null)
        Function<Object, String> f = { obj -> obj['value'] }
        def fetcher = PropertyDataFetcher.fetching(f)
        expect:
        fetcher.get(environment) == null
    }

    def "fetch via map lookup"() {
        def environment = env(["mapProperty": "aValue"])
        def fetcher = PropertyDataFetcher.fetching("mapProperty")
        expect:
        fetcher.get(environment) == "aValue"
    }

    def "fetch via public getter with private subclass"() {
        def environment = env(TestClass.createPackageProtectedImpl("aValue"))
        def fetcher = new PropertyDataFetcher("packageProtectedProperty")
        expect:
        fetcher.get(environment) == "aValue"
    }

    def "fetch via method that isn't present"() {
        def environment = env(new TestClass())
        def fetcher = new PropertyDataFetcher("valueNotPresent")
        def result = fetcher.get(environment)
        expect:
        result == null
    }

    def "fetch via method that is private"() {
        def environment = env(new TestClass())
        def fetcher = new PropertyDataFetcher("privateProperty")
        def result = fetcher.get(environment)
        expect:
        result == "privateValue"
    }

    def "fetch via public method"() {
        def environment = env(new TestClass())
        def fetcher = new PropertyDataFetcher("publicProperty")
        def result = fetcher.get(environment)
        expect:
        result == "publicValue"
    }

    def "fetch via public method declared two classes up"() {
        def environment = env(new TwoClassesDown("aValue"))
        def fetcher = new PropertyDataFetcher("publicProperty")
        def result = fetcher.get(environment)
        expect:
        result == "publicValue"
    }

    def "fetch via property only defined on package protected impl"() {
        def environment = env(TestClass.createPackageProtectedImpl("aValue"))
        def fetcher = new PropertyDataFetcher("propertyOnlyDefinedOnPackageProtectedImpl")
        def result = fetcher.get(environment)
        expect:
        result == "valueOnlyDefinedOnPackageProtectedIpl"
    }


    def "fetch via public field"() {
        def environment = env(new TestClass())
        def fetcher = new PropertyDataFetcher("publicField")
        def result = fetcher.get(environment)
        expect:
        result == "publicFieldValue"
    }

    def "fetch via private field"() {
        def environment = env(new TestClass())
        def fetcher = new PropertyDataFetcher("privateField")
        def result = fetcher.get(environment)
        expect:
        result == "privateFieldValue"
    }

    def "fetch when caching is in place has no bad effects"() {

        def environment = env(new TestClass())
        def fetcher = new PropertyDataFetcher("publicProperty")
        when:
        def result = fetcher.get(environment)
        then:
        result == "publicValue"

        when:
        result = fetcher.get(environment)
        then:
        result == "publicValue"

        when:
        PropertyDataFetcher.clearReflectionCache()
        result = fetcher.get(environment)
        then:
        result == "publicValue"


        when:
        fetcher = new PropertyDataFetcher("privateProperty")
        result = fetcher.get(environment)
        then:
        result == "privateValue"

        when:
        result = fetcher.get(environment)
        then:
        result == "privateValue"

        when:
        PropertyDataFetcher.clearReflectionCache()
        result = fetcher.get(environment)
        then:
        result == "privateValue"


        when:
        fetcher = new PropertyDataFetcher("publicField")
        result = fetcher.get(environment)
        then:
        result == "publicFieldValue"

        when:
        result = fetcher.get(environment)
        then:
        result == "publicFieldValue"

        when:
        PropertyDataFetcher.clearReflectionCache()
        result = fetcher.get(environment)
        then:
        result == "publicFieldValue"

        when:
        fetcher = new PropertyDataFetcher("unknownProperty")
        result = fetcher.get(environment)
        then:
        result == null

        when:
        result = fetcher.get(environment)
        then:
        result == null

        when:
        PropertyDataFetcher.clearReflectionCache()
        result = fetcher.get(environment)
        then:
        result == null

    }

    def "support for DFE on methods"() {
        def environment = env(new ClassWithDFEMethods())
        def fetcher = new PropertyDataFetcher("methodWithDFE")
        when:
        def result = fetcher.get(environment)
        then:
        result == "methodWithDFE"

        when:
        fetcher = new PropertyDataFetcher("methodWithoutDFE")
        result = fetcher.get(environment)
        then:
        result == "methodWithoutDFE"

        when:
        fetcher = new PropertyDataFetcher("defaultMethodWithDFE")
        result = fetcher.get(environment)
        then:
        result == "defaultMethodWithDFE"

        when:
        fetcher = new PropertyDataFetcher("defaultMethodWithoutDFE")
        result = fetcher.get(environment)
        then:
        result == "defaultMethodWithoutDFE"

        when:
        fetcher = new PropertyDataFetcher("methodWithTooManyArgs")
        result = fetcher.get(environment)
        then:
        result == null

        when:
        fetcher = new PropertyDataFetcher("defaultMethodWithTooManyArgs")
        result = fetcher.get(environment)
        then:
        result == null

        when:
        fetcher = new PropertyDataFetcher("methodWithOneArgButNotDataFetchingEnvironment")
        result = fetcher.get(environment)
        then:
        result == null

        when:
        fetcher = new PropertyDataFetcher("defaultMethodWithOneArgButNotDataFetchingEnvironment")
        result = fetcher.get(environment)
        then:
        result == null
    }

    def "ensure DFE is passed to method"() {

        def environment = env(new ClassWithDFEMethods())
        def fetcher = new PropertyDataFetcher("methodUsesDataFetchingEnvironment")
        when:
        def result = fetcher.get(environment)
        then:
        result == "value1"

        when:
        fetcher = new PropertyDataFetcher("defaultMethodUsesDataFetchingEnvironment")
        result = fetcher.get(environment)
        then:
        result == "value2"
    }
}
