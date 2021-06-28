package graphql.normalized;

import graphql.Assert;
import graphql.Internal;
import graphql.Mutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mutable
@Internal
public class IncludeCondition {

    /**
     * empty means not initialized. One empty SingleFieldCondition inside means there is no
     * condition and the field is always included.
     */
    private final List<SingleFieldCondition> singleFieldConditions = new ArrayList<>();

    public IncludeCondition(SingleFieldCondition singleFieldCondition) {
        this.singleFieldConditions.add(singleFieldCondition);
    }

    public IncludeCondition() {
    }

    public static final IncludeCondition DEFAULT_CONDITION = new IncludeCondition();


    public void addField(SingleFieldCondition singleFieldCondition) {
        this.singleFieldConditions.add(singleFieldCondition);
    }

    public void alwaysTrue() {
        this.singleFieldConditions.clear();
        this.singleFieldConditions.add(new SingleFieldCondition());
    }

    public boolean isAlwaysTrue() {
        return singleFieldConditions.size() == 1 && singleFieldConditions.get(0).isAlwaysTrue();
    }

    public boolean evaluate(Map<String, Object> coercedVariables) {
        Assert.assertTrue(singleFieldConditions.size() > 0, () -> "Expect at least on single file condition");
        for (SingleFieldCondition singleFieldCondition : singleFieldConditions) {
            if (singleFieldCondition.evaluate(coercedVariables)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return singleFieldConditions.toString();
    }
}
