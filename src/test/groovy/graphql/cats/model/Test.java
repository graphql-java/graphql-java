package graphql.cats.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {
    private String name;
    private Given given;
    private Action action;
    private List<Assertion> assertions;

    static List<Test> fromYaml(List<Map> tests) {
        return tests.stream().map(Test::makeTest).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Test makeTest(Map yaml) {
        Test test = new Test();
        test.name = (String) yaml.get("name");
        test.given = Given.fromYaml((Map) yaml.get("given"));
        test.action = Action.fromYaml((Map) yaml.get("when"));
        test.assertions = Assertion.fromYaml(Kit.listOfMaps(yaml.get("then")));
        return test;
    }


    public String getName() {
        return name;
    }

    public Given getGiven() {
        return given;
    }

    public Action getAction() {
        return action;
    }

    public List<Assertion> getAssertions() {
        return assertions;
    }

}
