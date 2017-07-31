package graphql.execution;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;


/**
 * The execution path represents the logical "path" that has been take to execute a field
 * during a request.
 * <p>
 * For example the graphql spec says that that path field of any error should be a list
 * of path entries - http://facebook.github.io/graphql/#sec-Errors and hence {@link #toList()}
 * can be called to provide this.
 */
@PublicApi
public class ExecutionPath {
    private static ExecutionPath ROOT_PATH = new ExecutionPath();

    public static ExecutionPath rootPath() {
        return ROOT_PATH;
    }

    private final ExecutionPath parent;
    private final PathSegment segment;

    private ExecutionPath() {
        parent = null;
        segment = null;
    }

    private ExecutionPath(ExecutionPath parent, PathSegment segment) {
        this.parent = assertNotNull(parent, "Must provide a parent path");
        this.segment = assertNotNull(segment, "Must provide a sub path");
    }

    public ExecutionPath segment(String segment) {
        return new ExecutionPath(this, new StringPathSegment(segment));
    }

    public ExecutionPath segment(int segment) {
        return new ExecutionPath(this, new IntPathSegment(segment));
    }

    public List<Object> toList() {
        if (parent == null) {
            return Collections.emptyList();
        }
        List<Object> list = new ArrayList<>();
        ExecutionPath p = this;
        while (p.segment != null) {
            list.add(p.segment.getValue());
            p = p.parent;
        }
        Collections.reverse(list);
        return list;
    }

    @Override
    public String toString() {
        if (parent == null) {
            return "";
        }

        if (parent == ROOT_PATH) {
            return segment.toString();
        }

        return parent.toString() + segment.toString();
    }

    public interface PathSegment<T> {
        T getValue();
    }

    private static class StringPathSegment implements PathSegment<String> {
        private final String value;

        StringPathSegment(String value) {
            assertTrue(value != null && !value.isEmpty(), "empty path component");
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return '/' + value;
        }
    }

    private static class IntPathSegment implements PathSegment<Integer> {
        private final int value;

        IntPathSegment(int value) {
            this.value = value;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "[" + value + ']';
        }
    }
}
