package graphql.cats.model;

import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unchecked")
public class Given {

    private String schema;
    private String schemaFile;
    private Object testData;
    private String testDataFile;
    private String query;

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

    public String getQuery() {
        return query;
    }

    static Given fromYaml(Map yaml) {
        Given given = new Given();
        given.schema = (String) yaml.get("schema");
        given.schemaFile = (String) yaml.get("schema-file");
        given.testData = yaml.get("test-data");
        given.testDataFile = (String) yaml.get("test-data-file");
        given.query = (String) yaml.get("query");
        return given;
    }
}
