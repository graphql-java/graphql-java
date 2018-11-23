package graphql.execution.validation;

import java.util.HashMap;
import java.util.Map;

class Comparisons {
    private final static Map<Class, Comparison> comparers = new HashMap<>();

    static Comparison findComparison(Class clazz) {
        return comparers.get(clazz);
    }

    static {
        comparers.put(Integer.class, new IntegerComparison());
    }


    static class IntegerComparison extends Comparison<Integer> {
        @Override
        int compareNumbers(Integer value, long comparedTo) {
            return Long.compare(value, comparedTo);
        }
    }

}
