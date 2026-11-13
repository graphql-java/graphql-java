package graphql.relay

import spock.lang.Specification

class DefaultPageInfoTest extends Specification {

    def "test equality and hashcode"() {
        expect:

        assert new DefaultPageInfo(new DefaultConnectionCursor(ls), new DefaultConnectionCursor(le), lp, ln).equals(
                new DefaultPageInfo(new DefaultConnectionCursor(rs), new DefaultConnectionCursor(re), rp, rn)) == isEqual

        assert (new DefaultPageInfo(new DefaultConnectionCursor(ls), new DefaultConnectionCursor(le), lp, ln).hashCode() ==
                new DefaultPageInfo(new DefaultConnectionCursor(rs), new DefaultConnectionCursor(re), rp, rn).hashCode()) == isEqual

        where:

        ls  | le  | lp    | ln    | rs  | re  | rp    | rn    || isEqual
        "a" | "b" | true  | true  | "a" | "b" | true  | true  || true
        "x" | "b" | true  | true  | "a" | "b" | true  | true  || false
        "a" | "x" | true  | true  | "a" | "b" | true  | true  || false
        "a" | "b" | false | true  | "a" | "b" | true  | true  || false
        "a" | "b" | true  | false | "a" | "b" | true  | true  || false
        "a" | "b" | true  | true  | "x" | "b" | true  | true  || false
        "a" | "b" | true  | true  | "a" | "x" | true  | true  || false
        "a" | "b" | true  | true  | "a" | "b" | false | true  || false
        "a" | "b" | true  | true  | "a" | "b" | true  | false || false
    }
}
