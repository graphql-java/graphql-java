package graphql.normalized;

import graphql.Internal;
import graphql.Mutable;

import java.util.ArrayList;
import java.util.List;

@Mutable
@Internal
public class IncludeCondition {

    private final List<SingleFieldCondition> singleFieldConditions = new ArrayList<>();

    public IncludeCondition(SingleFieldCondition singleFieldCondition) {
        this.singleFieldConditions.add(singleFieldCondition);
    }

    public IncludeCondition() {
    }

    public static final IncludeCondition DEFAULT_CONDITION = new IncludeCondition();
    ;


    public void addField(SingleFieldCondition singleFieldCondition) {
        this.singleFieldConditions.add(singleFieldCondition);
    }

    @Override
    public String toString() {
        return singleFieldConditions.toString();
    }
}
