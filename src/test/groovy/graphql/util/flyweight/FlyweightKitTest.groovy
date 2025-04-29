package graphql.util.flyweight

import spock.lang.Specification

class FlyweightKitTest extends Specification {

    class CacheKey {
        private String className;
        private String propertyName;

        CacheKey(String className, String propertyName) {
            this.className = className
            this.propertyName = propertyName
        }

        String getClassName() {
            return className
        }

        String getPropertyName() {
            return propertyName
        }
    }

    def "can compute objects with a 2 key map"() {
        def flyweightMap = new FlyweightKit.BiKeyMap<String, String, CacheKey>()
        when:
        def cacheKey1 = flyweightMap.computeIfAbsent("classX", "foo", { k1, k2 -> new CacheKey(k1, k2) })

        then:
        cacheKey1.getClassName() == "classX"
        cacheKey1.getPropertyName() == "foo"

        when:
        def cacheKey2 = flyweightMap.computeIfAbsent("classX", "foo", { k1, k2 -> new CacheKey(k1, k2) })

        then:
        cacheKey2 === cacheKey1

        when:
        def cacheKey3 = flyweightMap.computeIfAbsent("classY", "foo", { k1, k2 -> new CacheKey(k1, k2) })

        then:
        cacheKey3 !== cacheKey1
        cacheKey3.getClassName() == "classY"
        cacheKey3.getPropertyName() == "foo"

        when:
        def cacheKey4 = flyweightMap.computeIfAbsent("classY", "bar", { k1, k2 -> new CacheKey(k1, k2) })

        then:
        cacheKey4 !== cacheKey3
        cacheKey4.getClassName() == "classY"
        cacheKey4.getPropertyName() == "bar"

        when:
        def cacheKey5 = flyweightMap.computeIfAbsent("classY", "bar", { k1, k2 -> new CacheKey(k1, k2) })

        then:
        cacheKey5 === cacheKey4
    }

    def "can compute objects with a 3 key map"() {
        def flyweightMap = new FlyweightKit.TriKeyMap<String, String, Integer, String>()

        when:
        def value1 = flyweightMap.computeIfAbsent("a", "x", 1, { k1, k2, k3 -> "$k1:$k2:$k3" })

        then:
        value1 == "a:x:1"

        when:
        def value2 = flyweightMap.computeIfAbsent("a", "x", 1, { k1, k2, k3 -> "$k1:$k2:$k3" })

        then:
        value2 == "a:x:1"
        value2 === value1

        when:
        def value3 = flyweightMap.computeIfAbsent("a", "x", 2, { k1, k2, k3 -> "$k1:$k2:$k3" })

        then:
        value3 == "a:x:2"

        when:
        def value4 = flyweightMap.computeIfAbsent("b", "x", 1, { k1, k2, k3 -> "$k1:$k2:$k3" })

        then:
        value4 == "b:x:1"

        when:
        def value5 = flyweightMap.computeIfAbsent("b", "x", 2, { k1, k2, k3 -> "$k1:$k2:$k3" })

        then:
        value5 == "b:x:2"

        when:
        def value6 = flyweightMap.computeIfAbsent("b", "x", 2, { k1, k2, k3 -> "$k1:$k2:$k3" })

        then:
        value6 === value5
    }
}
