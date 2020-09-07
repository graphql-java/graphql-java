package graphql.execution;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class ValueUnboxers implements ValueUnboxer {
    private final List<ValueUnboxer> unboxers;

    public ValueUnboxers(ValueUnboxer... unboxers) {
        this.unboxers = ImmutableList.copyOf(unboxers);
    }

    public ValueUnboxers(List<ValueUnboxer> unboxers) {
        this.unboxers = ImmutableList.copyOf(unboxers);
    }

    @Override
    public Object unbox(Object object, ValueUnboxingContext context) {
        if (object == null) {
            return null; // no need to try unboxers
        }

        for (ValueUnboxer unboxer : unboxers) {
            Object unboxed = unboxer.unbox(object, context);
            if (unboxed != object) { // NaN != NaN, do we need to handle this case?
                return unboxed;
            }
        }
        return object;
    }

    /**
     * @return the list of unboxers
     */
    public List<ValueUnboxer> getUnboxers() {
        return unboxers;
    }

    /**
     * @param unboxers extra unboxers
     * @return new value unboxer with all current and extra unboxers
     */
    public ValueUnboxers withExtraUnboxers(ValueUnboxer... unboxers) {
        return new ValueUnboxers(ImmutableList.<ValueUnboxer>builder().addAll(this.unboxers).add(unboxers).build());
    }
}
