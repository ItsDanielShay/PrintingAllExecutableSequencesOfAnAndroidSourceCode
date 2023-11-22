package com.example.customoverwrittenidentifier;

import java.util.ArrayList;
import java.util.List;

public class Node {

    private  boolean visited = false;

    private int child_id = 0;

    private String name;

    private List<Node> children;
    private Node father;

    public Node(String name) {
        this.name = name;
        this.children = new ArrayList<>();
    }

    public void addChild(Node child) {
        children.add(child);
        child.child_id = children.size();
        child.setFather(this);
    }

    public Node getChild(int child_id) {
        for (Node child : this.children){
            if (child.child_id == child_id){
                return child;
            }
        }
        return null;
    }

    public Node getMostLeftUnVisitedChild() {
        for (Node child : children) {
            if (!child.isVisited()) {
                return child;
            }
        }
        return null;
    }


    public List<Node> getChildren() {
        return children;
    }

    public Node getFather() {
        return father;
    }

    // Make setFather private to control access
    private void setFather(Node father) {
        this.father = father;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean hasFather(){
        return this.getFather() != null;
    }

    public Node getRootNode(){
        Node root = this;
        while (root.hasFather()){
            root = root.getFather();
        }
        return root;
    }

    public boolean isVisited() {
        return visited;
    }

    public boolean isAllChildrenVisited() {
        boolean visited = true;

        for (Node child : children){
            if (!child.isVisited()) {
                visited = false;
                return visited;
            }
        }
        return visited;
    }

    public void setVisitedState(boolean visited) {
        this.visited = visited;
    }
}