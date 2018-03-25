package graphql.cats.runner;

import graphql.cats.model.Scenario;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class ScenarioLoader {

    public static Scenario load(String name) {
        InputStream yamlSource = ScenarioLoader.class.getClassLoader().getResourceAsStream(name);

        Yaml yaml = new Yaml();
        Map map = (Map) yaml.load(yamlSource);

        return Scenario.fromYaml(map);
    }
}
