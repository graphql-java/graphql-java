package graphql.schema

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.schema.fetching.ConfusedPojo
import graphql.schema.somepackage.ClassWithDFEMethods
import graphql.schema.somepackage.ClassWithInterfaces
import graphql.schema.somepackage.ClassWithInteritanceAndInterfaces
import graphql.schema.somepackage.RecordLikeClass
import graphql.schema.somepackage.RecordLikeTwoClassesDown
import graphql.schema.somepackage.TestClass
import graphql.schema.somepackage.TwoClassesDown
import spock.lang.Specification

import java.util.function.Function

import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment

@SuppressWarnings("GroovyUnusedDeclaration")
class PropertyDataFetcherTest extends Specification {

    void setup() {
        PropertyDataFetcher.setUseSetAccessible(true)
        PropertyDataFetcher.setUseNegativeCache(true)
        PropertyDataFetcher.clearReflectionCache()
        PropertyDataFetcherHelper.setUseLambdaFactory(true)
    }

    def env(obj) {
        newDataFetchingEnvironment()
                .source(obj)
                .arguments([argument1: "value1", argument2: "value2"])
                .build()
    }

    static class SomeObject {
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

    def "fetch via method that is private with setAccessible OFF"() {
        PropertyDataFetcher.setUseSetAccessible(false)
        def environment = env(new TestClass())
        def fetcher = new PropertyDataFetcher("privateProperty")
        def result = fetcher.get(environment)
        expect:
        result == null
    }

    def "fetch via record method"() {
        def environment = env(new RecordLikeClass())
        when:
        def fetcher = new PropertyDataFetcher("recordProperty")
        def result = fetcher.get(environment)
        then:
        result == "recordProperty"

        // caching works
        when:
        fetcher = new PropertyDataFetcher("recordProperty")
        result = fetcher.get(environment)
        then:
        result == "recordProperty"

        // recordArgumentMethod will not work because it takes a parameter
        when:
        fetcher = new PropertyDataFetcher("recordArgumentMethod")
        result = fetcher.get(environment)
        then:
        result == null

        // equals will not work because it takes a parameter
        when:
        fetcher = new PropertyDataFetcher("equals")
        result = fetcher.get(environment)
        then:
        result == null

        // we allow hashCode() and toString() because why not - they are valid property names
        // they might not be that useful but they can be accessed

        when:
        fetcher = new PropertyDataFetcher("hashCode")
        result = fetcher.get(environment)
        then:
        result == 666

        when:
        fetcher = new PropertyDataFetcher("toString")
        result = fetcher.get(environment)
        then:
        result == "toString"
    }

    def "can fetch record like methods that are public and on super classes"() {
        def environment = env(new RecordLikeTwoClassesDown())
        when:
        def fetcher = new PropertyDataFetcher("recordProperty")
        def result = fetcher.get(environment)
        then:
        result == "recordProperty"
    }

    def "fetch via record method without lambda support"() {
        PropertyDataFetcherHelper.setUseLambdaFactory(false)
        PropertyDataFetcherHelper.clearReflectionCache()

        when:
        def environment = env(new RecordLikeClass())
        def fetcher = new PropertyDataFetcher("recordProperty")
        def result = fetcher.get(environment)
        then:
        result == "recordProperty"

        when:
        environment = env(new RecordLikeTwoClassesDown())
        fetcher = new PropertyDataFetcher("recordProperty")
        result = fetcher.get(environment)
        then:
        result == "recordProperty"
    }

    def "fetch via record method without lambda support in preference to getter methods"() {
        PropertyDataFetcherHelper.setUseLambdaFactory(false)
        PropertyDataFetcherHelper.clearReflectionCache()

        when:
        def environment = env(new ConfusedPojo())
        def fetcher = new PropertyDataFetcher("recordLike")
        def result = fetcher.get(environment)
        then:
        result == "recordLike"
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
        when:
        def result = fetcher.get(environment)
        then:
        result == "publicValue"

        when:
        result = fetcher.get(environment)
        then:
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

    def "fetch via private field when setAccessible OFF"() {
        PropertyDataFetcher.setUseSetAccessible(false)
        def environment = env(new TestClass())
        def fetcher = new PropertyDataFetcher("privateField")
        def result = fetcher.get(environment)
        expect:
        result == null
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

    def "finds interface methods"() {
        when:
        def environment = env(new ClassWithInterfaces())
        def fetcher = new PropertyDataFetcher("methodYouMustImplement")
        def result = fetcher.get(environment)
        then:
        result == "methodYouMustImplement"

        when:
        fetcher = new PropertyDataFetcher("methodYouMustAlsoImplement")
        result = fetcher.get(environment)
        then:
        result == "methodYouMustAlsoImplement"

        when:
        fetcher = new PropertyDataFetcher("methodThatIsADefault")
        result = fetcher.get(environment)
        then:
        result == "methodThatIsADefault"

        when:
        fetcher = new PropertyDataFetcher("methodThatIsAlsoADefault")
        result = fetcher.get(environment)
        then:
        result == "methodThatIsAlsoADefault"

    }

    def "finds interface methods with inheritance"() {
        def environment = env(new ClassWithInteritanceAndInterfaces.StartingClass())

        when:
        def fetcher = new PropertyDataFetcher("methodYouMustImplement")
        def result = fetcher.get(environment)
        then:
        result == "methodYouMustImplement"

        when:
        fetcher = new PropertyDataFetcher("methodThatIsADefault")
        result = fetcher.get(environment)
        then:
        result == "methodThatIsADefault"

        def environment2 = env(new ClassWithInteritanceAndInterfaces.InheritedClass())

        when:
        fetcher = new PropertyDataFetcher("methodYouMustImplement")
        result = fetcher.get(environment2)
        then:
        result == "methodYouMustImplement"

        when:
        fetcher = new PropertyDataFetcher("methodThatIsADefault")
        result = fetcher.get(environment2)
        then:
        result == "methodThatIsADefault"

        when:
        fetcher = new PropertyDataFetcher("methodYouMustAlsoImplement")
        result = fetcher.get(environment2)
        then:
        result == "methodYouMustAlsoImplement"

        when:
        fetcher = new PropertyDataFetcher("methodThatIsAlsoADefault")
        result = fetcher.get(environment2)
        then:
        result == "methodThatIsAlsoADefault"
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

    def "negative caching works as expected"() {
        def environment = env(new ClassWithDFEMethods())
        def fetcher = new PropertyDataFetcher("doesNotExist")
        when:
        def result = fetcher.get(environment)
        then:
        result == null

        when:
        result = fetcher.get(environment)
        then:
        result == null

        when:
        PropertyDataFetcher.setUseNegativeCache(false)
        PropertyDataFetcher.clearReflectionCache()
        result = fetcher.get(environment)
        then:
        result == null

        when:
        PropertyDataFetcher.setUseNegativeCache(true)
        PropertyDataFetcher.clearReflectionCache()
        result = fetcher.get(environment)
        then:
        result == null

    }

    static class ProductDTO {
        String name
        String model
    }

    class ProductData {
        def data = [new ProductDTO(name: "Prado", model: "GLX"), new ProductDTO(name: "Camry", model: "Momento")]

        List<ProductDTO> getProducts(DataFetchingEnvironment env) {
            boolean reverse = env.getArgument("reverseNames")
            if (reverse) {
                return data.collect { product -> new ProductDTO(name: product.name.reverse(), model: product.model) }
            } else {
                return data
            }
        }
    }

    def "end to end test of property fetcher working"() {
        def spec = '''
            type Query {
                products(reverseNames : Boolean = false) : [Product]
            }
            
            type Product {
                name : String
                model : String
            }
        '''

        def graphQL = TestUtil.graphQL(spec).build()
        def executionInput = ExecutionInput.newExecutionInput().query('''
            {
                products(reverseNames : true) {
                    name
                    model
                }
            }
        ''').root(new ProductData()).build()

        when:
        def er = graphQL.execute(executionInput)
        then:
        er.errors.isEmpty()
        er.data == [products: [[name: "odarP", model: "GLX"], [name: "yrmaC", model: "Momento"]]]
    }

    interface Foo {
        String getSomething();
    }

    private static class Bar implements Foo {
        @Override
        String getSomething() {
            return "bar"
        }
    }

    private static class Baz extends Bar implements Foo {}

    def "search for private getter in class hierarchy"() {
        given:
        Bar bar = new Baz()
        PropertyDataFetcher propertyDataFetcher = new PropertyDataFetcher("something")
        def dfe = Mock(DataFetchingEnvironment)
        dfe.getSource() >> bar
        when:
        def result = propertyDataFetcher.get(dfe)

        then:
        result == "bar"

        // repeat - should be cached
        when:
        result = propertyDataFetcher.get(dfe)

        then:
        result == "bar"
    }

    def "issue 3247 - record like statics should not be used"() {
        given:
        def payload = new UpdateOrganizerSubscriptionPayload(true, new OrganizerSubscriptionError())
        PropertyDataFetcher propertyDataFetcher = new PropertyDataFetcher("success")
        def dfe = Mock(DataFetchingEnvironment)
        dfe.getSource() >> payload
        when:
        def result = propertyDataFetcher.get(dfe)

        then:
        result == true

        // repeat - should be cached
        when:
        result = propertyDataFetcher.get(dfe)

        then:
        result == true
    }

    def "issue 3247 - record like statics should not be found"() {
        given:
        def errorShape = new OrganizerSubscriptionError()
        PropertyDataFetcher propertyDataFetcher = new PropertyDataFetcher("message")
        def dfe = Mock(DataFetchingEnvironment)
        dfe.getSource() >> errorShape
        when:
        def result = propertyDataFetcher.get(dfe)

        then:
        result == null // not found as its a static recordLike() method

        // repeat - should be cached
        when:
        result = propertyDataFetcher.get(dfe)

        then:
        result == null
    }

    def "issue 3247 - getter statics should be found"() {
        given:
        def objectInQuestion = new BarClassWithStaticProperties()
        PropertyDataFetcher propertyDataFetcher = new PropertyDataFetcher("foo")
        def dfe = Mock(DataFetchingEnvironment)
        dfe.getSource() >> objectInQuestion
        when:
        def result = propertyDataFetcher.get(dfe)

        then:
        result == "foo"

        // repeat - should be cached
        when:
        result = propertyDataFetcher.get(dfe)

        then:
        result == "foo"

        when:
        propertyDataFetcher = new PropertyDataFetcher("bar")
        result = propertyDataFetcher.get(dfe)

        then:
        result == "bar"

        // repeat - should be cached
        when:
        result = propertyDataFetcher.get(dfe)

        then:
        result == "bar"
    }

    class BaseObject {
        private String id

        String getId() {
            return id
        }

        void setId(String value) {
            id = value;
        }
    }

    class OtherObject extends BaseObject {}

    def "Can access private property from base class that starts with i in Turkish"() {
        // see https://github.com/graphql-java/graphql-java/issues/3385
        given:
        Locale oldLocale = Locale.getDefault()
        Locale.setDefault(new Locale("tr", "TR"))

        def environment = env(new OtherObject(id: "aValue"))
        def fetcher = PropertyDataFetcher.fetching("id")

        when:
        String propValue = fetcher.get(environment)

        then:
        propValue == 'aValue'

        cleanup:
        Locale.setDefault(oldLocale)
    }
    /**
     * Classes from issue to ensure we reproduce as reported by customers
     *
     * In the UpdateOrganizerSubscriptionPayload class we will find the getSuccess() because static recordLike() methods are no longer allowed
     */
    static class OrganizerSubscriptionError {
        static String message() { return "error " }
    }

    static class UpdateOrganizerSubscriptionPayload {
        private final Boolean success
        private final OrganizerSubscriptionError error

        UpdateOrganizerSubscriptionPayload(Boolean success, OrganizerSubscriptionError error) {
            this.success = success
            this.error = error
        }

        static UpdateOrganizerSubscriptionPayload success() {
            // 👈 note the static factory method for creating a success payload
            return new UpdateOrganizerSubscriptionPayload(Boolean.TRUE, null)
        }

        static UpdateOrganizerSubscriptionPayload error(OrganizerSubscriptionError error) {
            // 👈 note the static factory method for creating a success payload
            return new UpdateOrganizerSubscriptionPayload(null, error)
        }

        Boolean getSuccess() {
            return success
        }

        OrganizerSubscriptionError getError() {
            return error
        }


        @Override
        String toString() {
            return new StringJoiner(
                    ", ", UpdateOrganizerSubscriptionPayload.class.getSimpleName() + "[", "]")
                    .add("success=" + success)
                    .add("error=" + error)
                    .toString()
        }
    }

    static class FooClassWithStaticProperties {
        static String getFoo() { return "foo" }
    }

    static class BarClassWithStaticProperties extends FooClassWithStaticProperties {
        static String getBar() { return "bar" }
    }
}
