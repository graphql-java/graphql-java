package graphql.cats.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class Action {

    private Execute execute;
    private Object parse;
    private List<String> validate;

    public Optional<Execute> getExecute() {
        return Optional.ofNullable(execute);
    }

    public boolean isParse() {
        return Optional.ofNullable(parse).isPresent();
    }

    public Optional<List<String>> getValidate() {
        return Optional.ofNullable(validate);
    }

    public static Action fromYaml(Map yaml) {
        Action action = new Action();
        if (yaml.containsKey("execute")) {
            action.execute = Execute.fromYaml((Map) yaml.get("execute"));
        }
        if (yaml.containsKey("parse")) {
            action.parse = true;
        }
        if (yaml.containsKey("validate")) {
            List<Object> validate = (List<Object>) yaml.get("validate");
            action.validate = validate.stream().map(Object::toString).collect(Collectors.toList());
        }
        return action;
    }
}
