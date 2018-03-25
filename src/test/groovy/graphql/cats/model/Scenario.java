package graphql.cats.model;

import java.util.List;
import java.util.Map;

public class Scenario {

    public static Scenario fromYaml(Map yaml) {
        Scenario scenario = new Scenario();
        scenario.scenario = (String) yaml.get("scenario");
        if (yaml.get("background") != null) {
            scenario.background = Background.fromYaml((Map) yaml.get("background"));
        }
        scenario.tests = Test.fromYaml(Kit.listOfMaps(yaml.get("tests")));
        return scenario;
    }

    private String scenario;
    private Background background;
    private List<Test> tests;

    public String getScenario() {
        return scenario;
    }

    public Background getBackground() {
        return background;
    }

    public List<Test> getTests() {
        return tests;
    }
}
