package graphql.execution;

import graphql.Assert;
import graphql.AssertException;
import graphql.PublicApi;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static java.lang.String.format;


/**
 * As a graphql query is executed, each field forms a hierarchical path from parent field to child field and this
 * class represents that path as a series of segments.
 */
@PublicApi
public class ExecutionPath {
    private static final ExecutionPath ROOT_PATH = new ExecutionPath();

    /**
     * All paths start from here
     *
     * @return the root path
     */
    public static ExecutionPath rootPath() {
        return ROOT_PATH;
    }

    private final ExecutionPath parent;
    private final Object segment;

    // hash is effective immutable but lazily initialized similar to the hash code of java.lang.String
    private int hash;

    private ExecutionPath() {
        parent = null;
        segment = null;
    }

    private ExecutionPath(ExecutionPath parent, String segment) {
        this.parent = assertNotNull(parent, () -> "Must provide a parent path");
        this.segment = assertNotNull(segment, () -> "Must provide a sub path");
    }

    private ExecutionPath(ExecutionPath parent, int segment) {
        this.parent = assertNotNull(parent, () -> "Must provide a parent path");
        this.segment = segment;
    }

    public int getLevel() {
        int counter = 0;
        ExecutionPath currentPath = this;
        while (currentPath != null) {
            if (currentPath.segment instanceof String) {
                counter++;
            }
            currentPath = currentPath.parent;
        }
        return counter;
    }

    public ExecutionPath getPathWithoutListEnd() {
        if (ROOT_PATH.equals(this)) {
            return ROOT_PATH;
        }
        if (segment instanceof String) {
            return this;
        }
        return parent;
    }

    /**
     * @return true if the end of the path has a list style segment eg 'a/b[2]'
     */
    public boolean isListSegment() {
        return segment instanceof Integer;
    }

    /**
     * @return true if the end of the path has a named style segment eg 'a/b[2]/c'
     */
    public boolean isNamedSegment() {
        return segment instanceof String;
    }


    public String getSegmentName() {
        return (String) segment;
    }

    public int getSegmentIndex() {
        return (int) segment;
    }

    public Object getSegmentValue() {
        return segment;
    }

    public ExecutionPath getParent() {
        return parent;
    }

    /**
     * Parses an execution path from the provided path string in the format /segment1/segment2[index]/segmentN
     *
     * @param pathString the path string
     *
     * @return a parsed execution path
     */
    public static ExecutionPath parse(String pathString) {
        pathString = pathString == null ? "" : pathString;
        String finalPathString = pathString.trim();
        StringTokenizer st = new StringTokenizer(finalPathString, "/[]", true);
        ExecutionPath path = ExecutionPath.rootPath();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if ("/".equals(token)) {
                assertTrue(st.hasMoreTokens(), () -> String.format(mkErrMsg(), finalPathString));
                path = path.segment(st.nextToken());
            } else if ("[".equals(token)) {
                assertTrue(st.countTokens() >= 2, () -> String.format(mkErrMsg(), finalPathString));
                path = path.segment(Integer.parseInt(st.nextToken()));
                String closingBrace = st.nextToken();
                assertTrue(closingBrace.equals("]"), () -> String.format(mkErrMsg(), finalPathString));
            } else {
                throw new AssertException(format(mkErrMsg(), pathString));
            }
        }
        return path;
    }

    /**
     * This will create an execution path from the list of objects
     *
     * @param objects the path objects
     *
     * @return a new execution path
     */
    public static ExecutionPath fromList(List<?> objects) {
        assertNotNull(objects);
        ExecutionPath path = ExecutionPath.rootPath();
        for (Object object : objects) {
            if (object instanceof String) {
                path = path.segment(((String) object));
            } else {
                path = path.segment((int) object);
            }
        }
        return path;
    }

    private static String mkErrMsg() {
        return "Invalid path string : '%s'";
    }

    /**
     * Takes the current path and adds a new segment to it, returning a new path
     *
     * @param segment the string path segment to add
     *
     * @return a new path containing that segment
     */
    public ExecutionPath segment(String segment) {
        return new ExecutionPath(this, segment);
    }

    /**
     * Takes the current path and adds a new segment to it, returning a new path
     *
     * @param segment the int path segment to add
     *
     * @return a new path containing that segment
     */
    public ExecutionPath segment(int segment) {
        return new ExecutionPath(this, segment);
    }

    /**
     * Drops the last segment off the path
     *
     * @return a new path with the last segment dropped off
     */
    public ExecutionPath dropSegment() {
        if (this == rootPath()) {
            return null;
        }
        return this.parent;
    }

    /**
     * Replaces the last segment on the path eg ExecutionPath.parse("/a/b[1]").replaceSegment(9)
     * equals "/a/b[9]"
     *
     * @param segment the integer segment to use
     *
     * @return a new path with the last segment replaced
     */
    public ExecutionPath replaceSegment(int segment) {
        Assert.assertTrue(!ROOT_PATH.equals(this), () -> "You MUST not call this with the root path");
        return new ExecutionPath(parent, segment);
    }

    /**
     * Replaces the last segment on the path eg ExecutionPath.parse("/a/b[1]").replaceSegment("x")
     * equals "/a/b/x"
     *
     * @param segment the string segment to use
     *
     * @return a new path with the last segment replaced
     */
    public ExecutionPath replaceSegment(String segment) {
        Assert.assertTrue(!ROOT_PATH.equals(this), () -> "You MUST not call this with the root path");
        return new ExecutionPath(parent, segment);
    }


    /**
     * @return true if the path is the {@link #rootPath()}
     */
    public boolean isRootPath() {
        return this == ROOT_PATH;
    }

    /**
     * Appends the provided path to the current one
     *
     * @param path the path to append
     *
     * @return a new path
     */
    public ExecutionPath append(ExecutionPath path) {
        List<Object> objects = this.toList();
        objects.addAll(assertNotNull(path).toList());
        return fromList(objects);
    }


    public ExecutionPath sibling(String siblingField) {
        Assert.assertTrue(!ROOT_PATH.equals(this), () -> "You MUST not call this with the root path");
        return new ExecutionPath(this.parent, siblingField);
    }

    public ExecutionPath sibling(int siblingField) {
        Assert.assertTrue(!ROOT_PATH.equals(this), () -> "You MUST not call this with the root path");
        return new ExecutionPath(this.parent, siblingField);
    }

    /**
     * @return converts the path into a list of segments
     */
    public List<Object> toList() {
        if (parent == null) {
            return new LinkedList<>();
        }
        LinkedList<Object> list = new LinkedList<>();
        ExecutionPath p = this;
        while (p.segment != null) {
            list.addFirst(p.segment);
            p = p.parent;
        }
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

        if (ROOT_PATH.equals(parent)) {
            return segmentToString();
        }

        return parent.toString() + segmentToString();
    }

    public String segmentToString() {
        if (segment instanceof String) {
            return "/" + segment;
        } else {
            return "[" + segment + "]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExecutionPath self = this;
        ExecutionPath that = (ExecutionPath) o;
        while (self.segment != null && that.segment != null) {
            if (!Objects.equals(self.segment, that.segment)) {
                return false;
            }
            self = self.parent;
            that = that.parent;
        }

        return self.isRootPath() && that.isRootPath();
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            h = 1;
            ExecutionPath self = this;
            while (self != null) {
                Object value = self.segment;
                h = 31 * h + (value == null ? 0 : value.hashCode());
                self = self.parent;
            }
            hash = h;
        }
        return h;
    }


}
