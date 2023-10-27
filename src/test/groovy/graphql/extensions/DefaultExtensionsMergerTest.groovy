package graphql.extensions

import com.google.common.collect.ImmutableMap
import spock.lang.Specification

class DefaultExtensionsMergerTest extends Specification {

    def merger = new DefaultExtensionsMerger()

    def "can merge maps"() {

        when:
        def actual = merger.merge(leftMap, rightMap)
        then:
        actual == expected
        where:
        leftMap                                         | rightMap                                                   | expected
        [:]                                             | [:]                                                        | ImmutableMap.of()
        ImmutableMap.of()                               | ImmutableMap.of()                                          | ImmutableMap.of()
        // additive
        [x: [firstName: "Brad"]]                        | [y: [lastName: "Baker"]]                                   | [x: [firstName: "Brad"], y: [lastName: "Baker"]]
        [x: "24", y: "25", z: "26"]                     | [a: "1", b: "2", c: "3"]                                   | [x: "24", y: "25", z: "26", a: "1", b: "2", c: "3"]
        // merge
        [key1: [firstName: "Brad"]]                     | [key1: [lastName: "Baker"]]                                | [key1: [firstName: "Brad", lastName: "Baker"]]

        // merge with right extra key
        [key1: [firstName: "Brad", middleName: "Leon"]] | [key1: [lastName: "Baker"], key2: [hobby: "graphql-java"]] | [key1: [firstName: "Brad", middleName: "Leon", lastName: "Baker"], key2: [hobby: "graphql-java"]]

    }

    def "can handle null entries"() {

        when:
        def actual = merger.merge(leftMap, rightMap)
        then:
        actual == expected
        where:
        leftMap                  | rightMap              | expected
        // nulls
        [x: [firstName: "Brad"]] | [y: [lastName: null]] | [x: [firstName: "Brad"], y: [lastName: null]]
    }

    def "prefers the right on conflict"() {

        when:
        def actual = merger.merge(leftMap, rightMap)
        then:
        actual == expected
        where:
        leftMap                                   | rightMap                                               | expected
        [x: [firstName: "Brad"]]                  | [x: [firstName: "Donna"]]                              | [x: [firstName: "Donna"]]
        [x: [firstName: "Brad"]]                  | [x: [firstName: "Donna", seenStarWars: true]]          | [x: [firstName: "Donna", seenStarWars: true]]
        [x: [firstName: "Brad", hates: "Python"]] | [x: [firstName: "Donna", seenStarWars: true]]          | [x: [firstName: "Donna", hates: "Python", seenStarWars: true]]


        // disparate types dont matter - it prefers the right
        [x: [firstName: "Brad"]]                  | [x: [firstName: [salutation: "Queen", name: "Donna"]]] | [x: [firstName: [salutation: "Queen", name: "Donna"]]]

    }

    def "it appends to lists"() {

        when:
        def actual = merger.merge(leftMap, rightMap)
        then:
        actual == expected
        where:
        leftMap           | rightMap          | expected
        [x: [1, 2, 3, 4]] | [x: [5, 6, 7, 8]] | [x: [1, 2, 3, 4, 5, 6, 7, 8]]
        //
        // truly additive - no object equality
        [x: [1, 2, 3]]    | [x: [1, 2, 3]]    | [x: [1, 2, 3, 1, 2, 3]]
        [x: []]           | [x: [1, 2, 3]]    | [x: [1, 2, 3]]
        [x: [null]]       | [x: [1, 2, 3]]    | [x: [null, 1, 2, 3]]
        //
        // prefers right if they are not both lists
        [x: null]         | [x: [1, 2, 3]]    | [x: [1, 2, 3]]
    }
}
