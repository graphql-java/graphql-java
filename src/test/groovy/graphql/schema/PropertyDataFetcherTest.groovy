package graphql.schema

import graphql.ExecutionInput
import graphql.Scalars
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

/**
 * Note : That `new PropertyDataFetcher("someProperty")` and `SingletonPropertyDataFetcher.singleton()`
 * should really be the equivalent since they both go via `PropertyDataFetcherHelper.getPropertyValue`
 * under the covers.
 *
 * But where we can we have tried to use `where` blocks to test both
 *
 */
@SuppressWarnings("GroovyUnusedDeclaration")
class PropertyDataFetcherTest extends Specification {

    void setup() {
        PropertyDataFetcher.setUseSetAccessible(true)
        PropertyDataFetcher.setUseNegativeCache(true)
        PropertyDataFetcher.clearReflectionCache()
        PropertyDataFetcherHelper.setUseLambdaFactory(true)
    }

    def env(String propertyName, Object obj) {
        def fieldDefinition = GraphQLFieldDefinition.newFieldDefinition().name(propertyName).type(Scalars.GraphQLString).build()
        newDataFetchingEnvironment()
                .source(obj)
                .fieldDefinition(fieldDefinition)
                .arguments([argument1: "value1", argument2: "value2"])
                .build()
    }

    static class SomeObject {
        String value
    }

    def "null source is always null"() {
        given:
        def environment = env("someProperty", null)

        expect:
        fetcher.get(environment) == null

        where:
        fetcher                                  | _
        new PropertyDataFetcher("someProperty")  | _
        SingletonPropertyDataFetcher.singleton() | _
    }

    def "function based fetcher works with non null source"() {
        def environment = env("notused", new SomeObject(value: "aValue"))
        Function<Object, String> f = { obj -> obj['value'] }
        def fetcher = PropertyDataFetcher.fetching(f)
        expect:
        fetcher.get(environment) == "aValue"
    }

    def "function based fetcher works with null source"() {
        def environment = env("notused", null)
        Function<Object, String> f = { obj -> obj['value'] }
        def fetcher = PropertyDataFetcher.fetching(f)
        expect:
        fetcher.get(environment) == null
    }

    def "fetch via map lookup"() {
        given:
        def environment = env("mapProperty", ["mapProperty": "aValue"])

        expect:
        fetcher.get(environment) == "aValue"

        where:
        fetcher                                     | _
        PropertyDataFetcher.fetching("mapProperty") | _
        SingletonPropertyDataFetcher.singleton()    | _
    }

    def "fetch via public getter with private subclass"() {
        given:
        def environment = env("packageProtectedProperty", TestClass.createPackageProtectedImpl("aValue"))

        expect:
        fetcher.get(environment) == "aValue"

        where:
        fetcher                                             | _
        new PropertyDataFetcher("packageProtectedProperty") | _
        SingletonPropertyDataFetcher.singleton()            | _
    }

    def "fetch via method that isn't present"() {
        given:
        def environment = env("valueNotPresent", new TestClass())

        when:
        def result = fetcher.get(environment)

        then:
        result == null

        where:
        fetcher                                    | _
        new PropertyDataFetcher("valueNotPresent") | _
        SingletonPropertyDataFetcher.singleton()   | _

    }

    def "fetch via method that is private"() {
        given:
        def environment = env("privateProperty", new TestClass())

        when:
        def result = fetcher.get(environment)

        then:
        result == "privateValue"

        where:
        fetcher                                    | _
        new PropertyDataFetcher("privateProperty") | _
        SingletonPropertyDataFetcher.singleton()   | _

    }

    def "fetch via method that is private with setAccessible OFF"() {
        given:
        PropertyDataFetcher.setUseSetAccessible(false)
        def environment = env("privateProperty", new TestClass())

        when:
        def result = fetcher.get(environment)

        then:
        result == null

        where:
        fetcher                                    | _
        new PropertyDataFetcher("privateProperty") | _
        SingletonPropertyDataFetcher.singleton()   | _

    }

    def "fetch via record method"() {
        given:
        def environment = env("recordProperty", new RecordLikeClass())

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

    def "fetch via record method with singleton fetcher"() {
        given:
        def environment = env("recordProperty", new RecordLikeClass())

        when:
        def fetcher = SingletonPropertyDataFetcher.singleton()
        def result = fetcher.get(environment)
        then:
        result == "recordProperty"
    }

    def "can fetch record like methods that are public and on super classes"() {
        given:
        def environment = env("recordProperty", new RecordLikeTwoClassesDown())

        when:
        def result = fetcher.get(environment)

        then:
        result == "recordProperty"

        where:
        fetcher                                   | _
        new PropertyDataFetcher("recordProperty") | _
        SingletonPropertyDataFetcher.singleton()  | _
    }

    def "fetch via record method without lambda support"() {
        given:
        PropertyDataFetcherHelper.setUseLambdaFactory(false)
        PropertyDataFetcherHelper.clearReflectionCache()

        when:
        def environment = env("recordProperty", new RecordLikeClass())
        def fetcher = new PropertyDataFetcher("recordProperty")
        def result = fetcher.get(environment)

        then:
        result == "recordProperty"

        when:
        environment = env("recordProperty", new RecordLikeTwoClassesDown())
        fetcher = new PropertyDataFetcher("recordProperty")
        result = fetcher.get(environment)

        then:
        result == "recordProperty"
    }

    def "fetch via record method without lambda support in preference to getter methods"() {
        given:
        PropertyDataFetcherHelper.setUseLambdaFactory(false)
        PropertyDataFetcherHelper.clearReflectionCache()

        when:
        def environment = env("recordLike", new ConfusedPojo())
        def result = fetcher.get(environment)

        then:
        result == "recordLike"

        where:
        fetcher                                  | _
        new PropertyDataFetcher("recordLike")    | _
        SingletonPropertyDataFetcher.singleton() | _
    }

    def "fetch via public method"() {
        given:
        def environment = env("publicProperty", new TestClass())

        when:
        def result = fetcher.get(environment)

        then:
        result == "publicValue"

        where:
        fetcher                                   | _
        new PropertyDataFetcher("publicProperty") | _
        SingletonPropertyDataFetcher.singleton()  | _

    }

    def "fetch via public method declared two classes up"() {
        given:
        def environment = env("publicProperty", new TwoClassesDown("aValue"))
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
        given:
        def environment = env("propertyOnlyDefinedOnPackageProtectedImpl", TestClass.createPackageProtectedImpl("aValue"))

        when:
        def result = fetcher.get(environment)

        then:
        result == "valueOnlyDefinedOnPackageProtectedIpl"

        where:
        fetcher                                                              | _
        new PropertyDataFetcher("propertyOnlyDefinedOnPackageProtectedImpl") | _
        SingletonPropertyDataFetcher.singleton()                             | _
    }


    def "fetch via public field"() {
        given:

        def environment = env("publicField", new TestClass())
        def result = fetcher.get(environment)

        expect:
        result == "publicFieldValue"

        where:
        fetcher                                  | _
        new PropertyDataFetcher("publicField")   | _
        SingletonPropertyDataFetcher.singleton() | _
    }

    def "fetch via private field"() {
        given:
        def environment = env("privateField", new TestClass())
        def result = fetcher.get(environment)

        expect:
        result == "privateFieldValue"

        where:
        fetcher                                  | _
        new PropertyDataFetcher("privateField")  | _
        SingletonPropertyDataFetcher.singleton() | _
    }

    def "fetch via private field when setAccessible OFF"() {
        given:
        PropertyDataFetcher.setUseSetAccessible(false)
        def environment = env("privateField", new TestClass())
        def result = fetcher.get(environment)

        expect:
        result == null

        where:
        fetcher                                  | _
        new PropertyDataFetcher("privateField")  | _
        SingletonPropertyDataFetcher.singleton() | _
    }

    def "fetch when caching is in place has no bad effects"() {

        def environment = env("publicProperty", new TestClass())
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
        given:
        def environment = env("methodWithDFE", new ClassWithDFEMethods())
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
        def environment = env("methodYouMustImplement", new ClassWithInterfaces())
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
        def environment = env("methodYouMustImplement", new ClassWithInteritanceAndInterfaces.StartingClass())

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

        def environment2 = env("methodYouMustImplement", new ClassWithInteritanceAndInterfaces.InheritedClass())

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

        def environment = env("methodUsesDataFetchingEnvironment", new ClassWithDFEMethods())
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
        def environment = env("doesNotExist", new ClassWithDFEMethods())
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
        def dfe = env("something", bar)

        when:
        def result = fetcher.get(dfe)

        then:
        result == "bar"

        // repeat - should be cached
        when:
        result = fetcher.get(dfe)

        then:
        result == "bar"

        where:
        fetcher                                  | _
        new PropertyDataFetcher("something")     | _
        SingletonPropertyDataFetcher.singleton() | _
    }

    def "issue 3247 - record like statics should not be used"() {
        given:
        def payload = new UpdateOrganizerSubscriptionPayload(true, new OrganizerSubscriptionError())
        def dfe = env("success", payload)

        when:
        def result = fetcher.get(dfe)

        then:
        result == true

        // repeat - should be cached
        when:
        result = fetcher.get(dfe)

        then:
        result == true

        where:
        fetcher                                  | _
        new PropertyDataFetcher("success")       | _
        SingletonPropertyDataFetcher.singleton() | _
    }

    def "issue 3247 - record like statics should not be found"() {
        given:
        def errorShape = new OrganizerSubscriptionError()
        def dfe = env("message", errorShape)

        when:
        def result = fetcher.get(dfe)

        then:
        result == null // not found as its a static recordLike() method

        // repeat - should be cached
        when:
        result = fetcher.get(dfe)

        then:
        result == null

        where:
        fetcher                                  | _
        new PropertyDataFetcher("message")       | _
        SingletonPropertyDataFetcher.singleton() | _
    }

    def "issue 3247 - getter statics should be found"() {
        given:
        def objectInQuestion = new BarClassWithStaticProperties()
        def dfe = env("foo", objectInQuestion)
        PropertyDataFetcher propertyDataFetcher = new PropertyDataFetcher("foo")

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

        def environment = env("id", new OtherObject(id: "aValue"))

        when:
        def fetcher = PropertyDataFetcher.fetching("id")
        String propValue = fetcher.get(environment)

        then:
        propValue == 'aValue'

        when:
        fetcher = SingletonPropertyDataFetcher.singleton()
        propValue = fetcher.get(environment)

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
