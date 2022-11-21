package graphql.schema.fetching

import spock.lang.Specification

class LambdaFetchingSupportTest extends Specification {

    def "can proxy Pojo methods"() {

        def pojo = new Pojo("Brad", 42)
        when:
        def getName = LambdaFetchingSupport.mkCallFunction(Pojo.class, "getName", String.class)
        def getAge = LambdaFetchingSupport.mkCallFunction(Pojo.class, "getAge", Integer.TYPE)

        then:
        getName.apply(pojo) == "Brad"
        getAge.apply(pojo) == 42
    }

    def "get make getters based on property names"() {
        def pojo = new Pojo("Brad", 42)
        when:
        def getter = LambdaFetchingSupport.createGetter(Pojo.class, "name")
        then:
        getter.isPresent()
        getter.get().apply(pojo) == "Brad"

        when:
        getter = LambdaFetchingSupport.createGetter(Pojo.class, "age")
        then:
        getter.isPresent()
        getter.get().apply(pojo) == 42

    }

    def "get make getters based on record like names"() {
        def pojo = new Pojo("Brad", 42)
        when:
        def getter = LambdaFetchingSupport.createGetter(Pojo.class, "recordLike")
        then:
        getter.isPresent()
        getter.get().apply(pojo) == "recordLike"

        //
        // record like getters will be found first - this is new behavior but more sensible behavior
        def confusedPojo = new ConfusedPojo()
        when:
        getter = LambdaFetchingSupport.createGetter(ConfusedPojo.class, "recordLike")
        then:
        getter.isPresent()
        getter.get().apply(confusedPojo) == "recordLike"

        // weird arse getter methods like `issues` versus `isSues`
        when:
        getter = LambdaFetchingSupport.createGetter(ConfusedPojo.class, "gettingConfused")
        then:
        getter.isPresent()
        getter.get().apply(confusedPojo) == "gettingConfused"

        when:
        getter = LambdaFetchingSupport.createGetter(ConfusedPojo.class, "tingConfused")
        then:
        getter.isPresent()
        getter.get().apply(confusedPojo) == "getTingConfused"

        // weird arse getter methods like `issues` versus `isSues`
        when:
        getter = LambdaFetchingSupport.createGetter(ConfusedPojo.class, "issues")
        then:
        getter.isPresent()
        getter.get().apply(confusedPojo) == true

        when:
        getter = LambdaFetchingSupport.createGetter(ConfusedPojo.class, "sues")
        then:
        getter.isPresent()
        getter.get().apply(confusedPojo) == false

    }

    def "will handle missing ones"() {

        when:
        def getter = LambdaFetchingSupport.createGetter(Pojo.class, "nameX")
        then:
        !getter.isPresent()
    }

    def "will handle weird ones"() {

        def pojo = new Pojo("Brad", 42)

        when:
        def getter = LambdaFetchingSupport.createGetter(Pojo.class, "get")
        then:
        getter.isPresent()
        getter.get().apply(pojo) == "get"

        when:
        getter = LambdaFetchingSupport.createGetter(Pojo.class, "is")
        then:
        getter.isPresent()
        getter.get().apply(pojo) == "is"
    }

    def "can handle boolean setters - is by preference"() {

        def pojo = new Pojo("Brad", 42)
        when:
        def getter = LambdaFetchingSupport.createGetter(Pojo.class, "interesting")
        then:
        getter.isPresent()
        getter.get().apply(pojo) == true

        when:
        getter = LambdaFetchingSupport.createGetter(Pojo.class, "alone")
        then:
        getter.isPresent()
        getter.get().apply(pojo) == true

        when:
        getter = LambdaFetchingSupport.createGetter(Pojo.class, "booleanAndNullish")
        then:
        getter.isPresent()
        getter.get().apply(pojo) == null
    }

    def "will ignore non public methods"() {

        when:
        def getter = LambdaFetchingSupport.createGetter(Pojo.class, "protectedLevelMethod")
        then:
        !getter.isPresent()

        when:
        getter = LambdaFetchingSupport.createGetter(Pojo.class, "privateLevelMethod")
        then:
        !getter.isPresent()

        when:
        getter = LambdaFetchingSupport.createGetter(Pojo.class, "packageLevelMethod")
        then:
        !getter.isPresent()
    }
}
