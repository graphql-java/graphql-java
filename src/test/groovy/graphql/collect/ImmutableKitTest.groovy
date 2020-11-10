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

    def "can make an immutable map of lists"() {
        when:
        ImmutableMap<String, ImmutableList<String>> map = ImmutableKit.toImmutableMapOfLists([a: ["a", "A"]])
        then:
        map.get("a") == ImmutableList.copyOf(["a", "A"])
        map.get("a") instanceof ImmutableList
    }

    def "can map a collections"() {
        when:
        def outputList = ImmutableKit.map(["quick", "brown", "fox"], { word -> word.reverse() })
        then:
        outputList == ["kciuq", "nworb", "xof"]
        outputList instanceof ImmutableList
    }
}
