package graphql.execution.validation;

@SuppressWarnings("unchecked")
abstract class Comparison<T> {

    int compare(Object value, long comparedTo) {
        return compareNumbers(castTo(value), comparedTo);
    }

    private T castTo(Object value) {
        return (T) value;
    }

    abstract int compareNumbers(T value, long comparedTo);

}
