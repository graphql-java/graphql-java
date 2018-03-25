package graphql.cats.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class Assertion {

    private Object passes;
    private Object syntaxError;
    private Object data;
    private Integer errorCount;
    private String error;
    private String errorRegex;
    private List<Map> loc;
    private String exception;

    static List<Assertion> fromYaml(List<Map> yaml) {
        return yaml.stream().map(Assertion::makeThen).collect(Collectors.toList());
    }

    private static Assertion makeThen(Map yaml) {
        Assertion assertion = new Assertion();
        assertion.passes = yaml.containsKey("passes") ? true : null;
        assertion.syntaxError = yaml.get("syntax-error");
        assertion.data = yaml.get("data");
        assertion.errorCount = (Integer) yaml.get("errorCount");
        assertion.error = (String) yaml.get("error");
        assertion.errorRegex = (String) yaml.get("error-regex");
        assertion.loc = Kit.listOfMaps(yaml.get("loc"));
        assertion.exception = (String) yaml.get("exception");
        return assertion;
    }

    public Optional<Object> getPasses() {
        return Optional.ofNullable(passes);
    }

    public Optional<Object> getSytaxError() {
        return Optional.ofNullable(syntaxError);
    }

    public Optional<Object> getData() {
        return Optional.ofNullable(data);
    }

    public Optional<Integer> getErrorCount() {
        return Optional.ofNullable(errorCount);
    }

    public Optional<String> getError() {
        return Optional.ofNullable(error);
    }

    public Optional<String> getErrorRegex() {
        return Optional.ofNullable(errorRegex);
    }

    public Optional<List<Map>> getLoc() {
        return Optional.ofNullable(loc);
    }

    public Optional<String> getException() {
        return Optional.ofNullable(exception);
    }

}
