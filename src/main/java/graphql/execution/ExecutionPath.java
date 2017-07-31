package graphql.execution;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;


/**
 * As a graphql query is executed, each field forms a hierarchical path from parent field to child field and this
 * class represents that path as a series of segments.
 */
@PublicApi
public class ExecutionPath {
    private static ExecutionPath ROOT_PATH = new ExecutionPath();

    /**
     * All paths start from here
     *
     * @return the root path
     */
    public static ExecutionPath rootPath() {
        return ROOT_PATH;
    }

    private final ExecutionPath parent;
    private final PathSegment segment;
    private final List<Object> pathList;

    private ExecutionPath() {
        parent = null;
        segment = null;
        pathList = toListImpl();
    }

    private ExecutionPath(ExecutionPath parent, PathSegment segment) {
        this.parent = assertNotNull(parent, "Must provide a parent path");
        this.segment = assertNotNull(segment, "Must provide a sub path");
        pathList = toListImpl();
    }

    /**
     * Takes the current path and adds a new segment to it, returning a new path
     *
     * @param segment the string path segment to add
     *
     * @return a new path containing that segment
     */
    public ExecutionPath segment(String segment) {
        return new ExecutionPath(this, new StringPathSegment(segment));
    }

    /**
     * Takes the current path and adds a new segment to it, returning a new path
     *
     * @param segment the int path segment to add
     *
     * @return a new path containing that segment
     */
    public ExecutionPath segment(int segment) {
        return new ExecutionPath(this, new IntPathSegment(segment));
    }

    /**
     * @return converts the path into a list of segments
     */
    public List<Object> toList() {
        return pathList;
    }

    private List<Object> toListImpl() {
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

    /**
     * @return the path as a string which represents the call hierarchy
     */
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExecutionPath that = (ExecutionPath) o;

        return pathList.equals(that.pathList);
    }

    @Override
    public int hashCode() {
        return pathList.hashCode();
    }

    public interface PathSegment<T> {
        T getValue();
    }

    private static class StringPathSegment implements PathSegment<String> {
        private final String value;

        public StringPathSegment(String value) {
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

        public IntPathSegment(int value) {
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
