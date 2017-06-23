package graphql.language

import spock.lang.Specification

class DirectiveTest extends Specification {

    def "can get arguments correctly"() {
        Directive d1 = new Directive("d1",
                [
                        new Argument("a1", new StringValue("v1")),
                        new Argument("a2", new StringValue("v2")),
                        new Argument("repeated", new StringValue("r1")),
                        new Argument("repeated", new StringValue("r2")),
                ])

        expect:
        d1.getArguments().size() == 4
        //
        // other parts of the system ensure that repeated args are invalid, but if we manually create them
        // we always return the first
        d1.getArgumentsByName().size() == 3
        d1.getArgument("null") == null
        d1.getArgument("a1").getValue().isEqualTo(new StringValue("v1"))
        d1.getArgument("repeated").getValue().isEqualTo(new StringValue("r1"))

        d1.getArgumentsByName().get("null") == null
        d1.getArgumentsByName().get("a1").getValue().isEqualTo(new StringValue("v1"))
        d1.getArgumentsByName().get("repeated").getValue().isEqualTo(new StringValue("r1"))
    }

    def "list of directives can be turned into a map"() {
        def d1 = new Directive("d1")
        List<Directive> directives = [
                d1,
                new Directive("d2"),
                new Directive("repeated", [new Argument("a1", new StringValue("v1"))]),
                new Directive("repeated", [new Argument("a1", new StringValue("v2"))]),
        ]
        def directivesMap = NodeUtil.directivesByName(directives)

        expect:

        //
        // other parts of the system ensure that repeated directives are invalid, but if we manually create them
        // we always return the first

        directivesMap.size() == 3
        directivesMap.get("d1") == d1
        directivesMap.get("null") == null
        directivesMap.get("repeated").getArgument("a1").getValue().isEqualTo(new StringValue("v1"))
    }
}
