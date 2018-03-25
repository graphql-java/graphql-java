package graphql.cats.model;

import java.util.Map;
import java.util.Optional;

public class Background {
    private String schema;
    private String schemaFile;
    private Object testData;
    private String testDataFile;

    static Background fromYaml(Map yaml) {
        Background background = new Background();
        background.schema = (String) yaml.get("schema");
        background.schemaFile = (String) yaml.get("schema-file");
        background.testData = yaml.get("test-data");
        background.testDataFile = (String) yaml.get("test-test-data-file");
        return background;
    }

    public Optional<String> getSchema() {
        return Optional.ofNullable(schema);
    }

    public Optional<String> getSchemaFile() {
        return Optional.ofNullable(schemaFile);
    }

    public <T> Optional<T> getTestData() {
        return Optional.ofNullable((T) testData);
    }

    public Optional<String> getTestDataFile() {
        return Optional.ofNullable(testDataFile);
    }
}
