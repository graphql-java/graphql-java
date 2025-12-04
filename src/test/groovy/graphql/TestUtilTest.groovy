package graphql

import spock.lang.Specification

class TestUtilTest extends Specification {

    def "list contains in order"() {
        given:
        def masterList = ["a", "b", "c", "d", "e", "f", "g"]

        when:
        def actual = TestUtil.listContainsInOrder(masterList, subList)

        then:
        actual == expected

        where:
        subList                                  | expected
        ["a"]                                    | true
        ["f"]                                    | true
        ["c", "d"]                               | true
        ["f", "g"]                               | true
        ["a", "b", "c", "d", "e", "f", "g"]      | true
        ["f", "g", "X"]                          | false
        ["b", "c", "e"]                          | false
        []                                       | false
        ["a", "b", "c", "d", "e", "f", "g", "X"] | false
        ["A", "B", "C"]                          | false
    }

    def "list contains in order edge cases"() {
        when:
        def actual = TestUtil.listContainsInOrder([], [])
        then:
        !actual

        when:
        actual = TestUtil.listContainsInOrder(["a"], [])
        then:
        !actual

        when:
        actual = TestUtil.listContainsInOrder([], ["a"])
        then:
        !actual

        when:
        actual = TestUtil.listContainsInOrder([null, "a", null], [null, "a"])
        then:
        actual
    }

    def "list contains in order many lists"() {
        def master = ["a", "b", "c", "d", "e", "f", "g"]
        when:
        def actual = TestUtil.listContainsInOrder(master,
                ["b", "c"], ["e", "f"])
        then:
        actual

        when:
        actual = TestUtil.listContainsInOrder(master,
                ["b", "c"], ["g"])
        then:
        actual

        when:
        actual = TestUtil.listContainsInOrder(master,
                ["b", "c"], ["f", "g", "X"])
        then:
        !actual

        when:
        actual = TestUtil.listContainsInOrder(master,
                ["a"], ["b"], ["c"], ["d"], ["e"], ["f"], ["g"])
        then:
        actual

        when:
        actual = TestUtil.listContainsInOrder(master,
                ["a", "b"], ["c", "d"], ["e"], ["f"], ["g"])
        then:
        actual

        when:
        actual = TestUtil.listContainsInOrder(master,
                ["a"], ["b"], ["c"], ["d"], ["e"], ["f"], ["g"], ["X"])
        then:
        !actual

        when: "empty"
        actual = TestUtil.listContainsInOrder(master,
                ["a"], [], ["c"])
        then:
        !actual

        when:
        actual = TestUtil.listContainsInOrder(master,
                ["a"], ["b"], ["c"], ["X"], ["e"], ["f"], ["g"])
        then:
        !actual

    }
}
