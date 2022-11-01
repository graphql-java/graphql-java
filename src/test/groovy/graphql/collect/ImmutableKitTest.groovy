package graphql.collect

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import spock.lang.Specification

class ImmutableKitTest extends Specification {

    def "empty things are empty"() {
        expect:
        ImmutableKit.emptyMap().size() == 0
        ImmutableKit.emptyList().size() == 0
        ImmutableKit.emptyMap().size() == 0
    }

    def "can map a collections"() {
        when:
        def outputList = ImmutableKit.map(["quick", "brown", "fox"], { word -> word.reverse() })
        then:
        outputList == ["kciuq", "nworb", "xof"]
        outputList instanceof ImmutableList
    }

    def "can map a collection with nulls"() {
        when:
        def outputList = ImmutableKit.mapAndDropNulls(["quick", "brown", "NULL", "fox"], {
            word -> (word == "NULL") ? null : word.reverse()
        })
        then:
        outputList == ["kciuq", "nworb", "xof"]
        outputList instanceof ImmutableList
    }

    def "can add to lists"() {
        def list = ["a"]

        when:
        list = ImmutableKit.addToList(list, "b")
        then:
        list == ["a", "b"]

        when:
        list = ImmutableKit.addToList(list, "c", "d", "e")
        then:
        list == ["a", "b", "c", "d", "e"]
    }

    def "can add to sets"() {
        def set = new HashSet(["a"])

        when:
        set = ImmutableKit.addToSet(set, "b")
        then:
        set == ["a", "b"] as Set

        when:
        set = ImmutableKit.addToSet(set, "c", "d", "e")
        then:
        set == ["a", "b", "c", "d", "e"] as Set

        when:
        set = ImmutableKit.addToSet(set, "c", "d", "e", "f")
        then:
        set == ["a", "b", "c", "d", "e", "f"] as Set
    }
}
