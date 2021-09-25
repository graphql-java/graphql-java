package graphql.language;

import com.google.common.collect.ImmutableList;
import graphql.PublicApi;

import java.io.Serializable;
import java.util.List;

import static graphql.collect.ImmutableKit.emptyList;

/**
 * Graphql syntax has a series of characters, such as spaces, new lines and commas that are not considered relevant
 * to the syntax.  However they can be captured and associated with the AST elements they belong to.
 *
 * This costs more memory but for certain use cases (like editors) this maybe be useful
 */
@PublicApi
public class IgnoredChars implements Serializable {

    private final ImmutableList<IgnoredChar> left;
    private final ImmutableList<IgnoredChar> right;

    public static final IgnoredChars EMPTY = new IgnoredChars(emptyList(), emptyList());

    public IgnoredChars(List<IgnoredChar> left, List<IgnoredChar> right) {
        this.left = ImmutableList.copyOf(left);
        this.right = ImmutableList.copyOf(right);
    }


    public List<IgnoredChar> getLeft() {
        return left;
    }

    public List<IgnoredChar> getRight() {
        return right;
    }
}
