package graphql.util.querygenerator;

import java.util.*;

class TreeNode<T> {
    T value;
    List<TreeNode<T>> children;

    TreeNode(T value) {
        this.value = value;
        this.children = new ArrayList<>();
    }

    void addChild(TreeNode<T> child) {
        children.add(child);
    }
}


public class TreeTraversal {
    public static <T> void breadthFirstTraversal(TreeNode<T> root) {
        if (root == null) {
            return;
        }

        Queue<TreeNode<T>> queue = new LinkedList<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            TreeNode<T> current = queue.poll();
            System.out.println(current.value);

            for (TreeNode<T> child : current.children) {
                queue.add(child);
            }
        }
    }

    public static <T> void depthFirstTraversal(TreeNode<T> root) {
        if (root == null) {
            return;
        }

        System.out.println(root.value);

        for (TreeNode<T> child : root.children) {
            depthFirstTraversal(child);
        }
    }

    public static void main(String[] args) {
        TreeNode<String> root = new TreeNode<>("A");
        TreeNode<String> b = new TreeNode<>("B");
        TreeNode<String> c = new TreeNode<>("C");
        TreeNode<String> d = new TreeNode<>("D");
        TreeNode<String> e = new TreeNode<>("E");
        TreeNode<String> f = new TreeNode<>("F");
        TreeNode<String> x = new TreeNode<>("x");
        TreeNode<String> y = new TreeNode<>("y");

        f.addChild(y);
        root.addChild(b);
        root.addChild(c);
        root.addChild(x);
        b.addChild(d);
        b.addChild(e);
        c.addChild(f);

        breadthFirstTraversal(root);

        System.out.println("-----");

        depthFirstTraversal(root);
    }
}