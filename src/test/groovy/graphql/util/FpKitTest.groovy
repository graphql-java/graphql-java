package graphql.util

import com.google.common.collect.ImmutableList
import spock.lang.Specification

import java.util.function.Supplier

class FpKitTest extends Specification {

    class IterableThing implements Iterable {
        Iterable delegate

        IterableThing(Iterable delegate) {
            this.delegate = delegate
        }

        @Override
        Iterator iterator() {
            return delegate.iterator()
        }
    }

    def "toCollection works as expected"() {
        def expected = ["a", "b", "c"]
        def actual

        when:
        def array = ["a", "b", "c"].toArray()
        actual = FpKit.toCollection(array)
        then:
        actual == expected

        when:
        Set set = ["a", "b", "c"].toSet()
        actual = FpKit.toCollection(set)
        then:
        actual == expected.toSet()

        when:
        List list = ["a", "b", "c"].toList()
        actual = FpKit.toCollection(list)
        then:
        actual == expected

        when:
        IterableThing iterableThing = new IterableThing(["a", "b", "c"])
        actual = FpKit.toCollection(iterableThing)
        then:
        actual == expected
    }

    void "memoized supplier"() {

        def count = 0
        Supplier<Integer> supplier = { -> count++; return count }

        when:
        def memoizeSupplier = FpKit.intraThreadMemoize(supplier)
        def val1 = supplier.get()
        def val2 = supplier.get()
        def memoVal1 = memoizeSupplier.get()
        def memoVal2 = memoizeSupplier.get()

        then:
        val1 == 1
        val2 == 2

        memoVal1 == 3
        memoVal2 == 3
    }

    def "toListOrSingletonList works"() {
        def birdArr = ["Parrot", "Cockatiel", "Pigeon"] as String[]

        when:
        def l = FpKit.toListOrSingletonList(birdArr)
        then:
        l == ["Parrot", "Cockatiel", "Pigeon"]

        when:
        l = FpKit.toListOrSingletonList(["Parrot", "Cockatiel", "Pigeon"])
        then:
        l == ["Parrot", "Cockatiel", "Pigeon"]

        when:
        l = FpKit.toListOrSingletonList(["Parrot", "Cockatiel", "Pigeon"].stream())
        then:
        l == ["Parrot", "Cockatiel", "Pigeon"]

        when:
        l = FpKit.toListOrSingletonList(["Parrot", "Cockatiel", "Pigeon"].stream().iterator())
        then:
        l == ["Parrot", "Cockatiel", "Pigeon"]

        when:
        l = FpKit.toListOrSingletonList("Parrot")
        then:
        l == ["Parrot"]
    }

    class Person {
        String name
        String city

        Person(String name) {
            this.name = name
        }

        Person(String name, String city) {
            this.name = name
            this.city = city
        }

        String getName() {
            return name
        }

        String getCity() {
            return city
        }
    }

    def a = new Person("a", "New York")
    def b = new Person("b", "New York")
    def c1 = new Person("c", "Sydney")
    def c2 = new Person("c", "London")

    def "getByName tests"() {

        when:
        def map = FpKit.getByName([a, b, c1, c2], { it -> it.getName() })
        then:
        map == ["a": a, "b": b, c: c1]

        when:
        map = FpKit.getByName([a, b, c1, c2], { it -> it.getName() }, { it1, it2 -> it2 })
        then:
        map == ["a": a, "b": b, c: c2]
    }

    def "groupingBy tests"() {

        when:
        Map<String, ImmutableList<Person>> map = FpKit.groupingBy([a, b, c1, c2], { it -> it.getCity() })
        then:
        map == ["New York": [a, b], "Sydney": [c1], "London": [c2]]

        when:
        map = FpKit.filterAndGroupingBy([a, b, c1, c2], { it -> it != c1 }, { it -> it.getCity() })
        then:
        map == ["New York": [a, b], "London": [c2]]

    }

    def "toMapByUniqueKey works"() {

        when:
        Map<String, Person> map = FpKit.toMapByUniqueKey([a, b, c1], { it -> it.getName() })
        then:
        map == ["a": a, "b": b, "c": c1]

        when:
        FpKit.toMapByUniqueKey([a, b, c1, c2], { it -> it.getName() })
        then:
        def e = thrown(IllegalStateException.class)
        e.message.contains("Duplicate key")
    }

    def "findOne test"() {
        when:
        def opt = FpKit.findOne([a, b, c1, c2], { it -> it.getName() == "c" })
        then:
        opt.isPresent()
        opt.get() == c1

        when:
        opt = FpKit.findOne([a, b, c1, c2], { it -> it.getName() == "d" })
        then:
        opt.isEmpty()

        when:
        opt = FpKit.findOne([a, b, c1, c2], { it -> it.getName() == "a" })
        then:
        opt.isPresent()
        opt.get() == a
    }

    def "filterList works"() {
        when:
        def list = FpKit.filterList([a, b, c1, c2], { it -> it.getName() == "c" })
        then:
        list == [c1, c2]
    }

    def "set intersection works"() {
        def set1 = ["A","B","C"] as Set
        def set2 = ["A","C","D"] as Set
        def singleSetA = ["A"] as Set
        def disjointSet = ["X","Y"] as Set

        when:
        def intersection = FpKit.intersection(set1, set2)
        then:
        intersection == ["A","C"] as Set

        when: // reversed parameters
        intersection = FpKit.intersection(set2, set1)
        then:
        intersection == ["A","C"] as Set

        when: // singles
        intersection = FpKit.intersection(set1, singleSetA)
        then:
        intersection == ["A"] as Set

        when: // singles reversed
        intersection = FpKit.intersection(singleSetA, set1)
        then:
        intersection == ["A"] as Set

        when: // disjoint
        intersection = FpKit.intersection(set1, disjointSet)
        then:
        intersection.isEmpty()

        when: // disjoint reversed
        intersection = FpKit.intersection(disjointSet,set1)
        then:
        intersection.isEmpty()
    }
}
