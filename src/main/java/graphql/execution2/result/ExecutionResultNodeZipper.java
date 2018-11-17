package graphql.execution2.result;

import graphql.Assert;

import java.util.ArrayList;
import java.util.List;

public class ExecutionResultNodeZipper {

    private final ExecutionResultNode curNode;
    // from curNode upwards
    private final List<Breadcrumb> breadcrumbList;

    public ExecutionResultNodeZipper(ExecutionResultNode curNode, List<Breadcrumb> breadcrumbs) {
        Assert.assertNotNull(breadcrumbs, "breadcrumbs can't be null");
        Assert.assertNotNull(curNode, "curNode can't be null");
        this.curNode = curNode;
        this.breadcrumbList = new ArrayList<>(breadcrumbs);
    }

    public ExecutionResultNode getCurNode() {
        return curNode;
    }

    public List<Breadcrumb> getBreadcrumbList() {
        return new ArrayList<>(breadcrumbList);
    }

    public ExecutionResultNode getRootNode() {
        if (breadcrumbList.size() == 0) {
            return curNode;
        }
        return breadcrumbList.get(breadcrumbList.size() - 1).node;
    }

    public ExecutionResultNode getParent() {
        return breadcrumbList.get(0).node;
    }

    public ExecutionResultNodeZipper withNode(ExecutionResultNode newNode) {
        return new ExecutionResultNodeZipper(newNode, breadcrumbList);
    }

    public ExecutionResultNodeZipper moveUp() {
        Assert.assertTrue(breadcrumbList.size() > 0, "no parent");
        Breadcrumb breadCrumb = breadcrumbList.get(0);
        ExecutionResultNode parent = breadCrumb.node.withChild(curNode, breadCrumb.position);
        return new ExecutionResultNodeZipper(parent, breadcrumbList.subList(1, breadcrumbList.size()));
    }

    public ExecutionResultNode toRootNode() {
        ExecutionResultNode curRoot = curNode;
        for (Breadcrumb breadcrumb : breadcrumbList) {
            curRoot = breadcrumb.node.withChild(curRoot, breadcrumb.position);
        }
        return curRoot;
    }


    @Override
    public String toString() {
        return "ExecutionResultNodeZipper{" +
                "curNode=" + curNode +
                ", breadcrumbList=" + breadcrumbList +
                '}';
    }
}