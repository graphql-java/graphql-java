package graphql.execution.instrumentation.dataloader

import spock.lang.Specification

class LevelMapTest extends Specification {

    def "increase adds levels"() {
        given:
        LevelMap sut = new LevelMap(0) // starts empty

        when:
        sut.increment(2, 42) // level 2 has count 42

        then:
        sut.get(0) == 0
        sut.get(1) == 0
        sut.get(2) == 42
    }

    def "increase count by 10 for every level"() {
        given:
        LevelMap sut = new LevelMap(0)

        when:
        5.times {Integer level ->
            sut.increment(level, 10)
        }

        then:
        5.times { Integer level ->
            sut.get(level) == 10
        }
    }
    def "increase yields new count"() {
        given:
        LevelMap sut = new LevelMap(0)

        when:
        sut.increment(1, 0)

        then:
        sut.get(1) == 0

        when:
        sut.increment(1, 1)

        then:
        sut.get(1) == 1

        when:
        sut.increment(1, 100)

        then:
        sut.get(1) == 101
    }

    def "set yields new value"() {
        given:
        LevelMap sut = new LevelMap(0)

        when:
        sut.set(1, 1)

        then:
        sut.get(1) == 1

        when:
        sut.increment(1, 100)

        then:
        sut.get(1) == 101

        when:
        sut.set(1, 666)

        then:
        sut.get(1) == 666
    }

    def "toString() is important for debugging"() {
        given:
        LevelMap sut = new LevelMap(0)

        when:
        sut.toString()

        then:
        sut.toString() == "IntMap[]"

        when:
        sut.increment(0, 42)

        then:
        sut.toString() == "IntMap[level=0,count=42 ]"

        when:
        sut.increment(1, 1)

        then:
        sut.toString() == "IntMap[level=0,count=42 level=1,count=1 ]"
    }
}
