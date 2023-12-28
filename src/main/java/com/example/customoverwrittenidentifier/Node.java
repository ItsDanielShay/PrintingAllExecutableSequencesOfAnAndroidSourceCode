package com.example.customoverwrittenidentifier;

import java.util.ArrayList;
import java.util.List;

public class Node {

    private  boolean visited = false;

    private NodeTypes nodeType;

    public enum NodeTypes {
        METHOD, CONDITION
    }

    private String methodName;

    private List<Node> children = new ArrayList<>();

    private Node mainFather;

    private List<Node> fathers = new ArrayList<>();

    public Node(String methodName) {
        this.methodName = methodName;
        this.mainFather = this;   // when ever a node is initialized, its mainFather will be itself by default. (*)
    }

    public void addChild(Node child) {
        children.add(child);
        child.addFather(this);
        child.mainFather = this.mainFather;  // children will be introduced to their main father by their most recent father. (*)
    }

    //----------------------------------------------------------------------------------------------------------------------------//
    // These two statements (with this tag (*)) will ensure the program that any node knows the main father. At the beginning,    //
    // each node considers itself as the main father. However, if the node is added as child of another node, it will be          //
    // introduced to the main father by the most recent father. Noteworthy, if a child sets the main father as its child, it will //
    // introduce the main father as the main father again.                                                                        //
    //----------------------------------------------------------------------------------------------------------------------------//

    public List<Node> getChildren() {
        return children;
    }

    public List<Node> getFathers() {
        return fathers;
    }

    public void setAsMainFather(){
        mainFather = this;
    }

    public Node getMainFather(){
        return mainFather;
    }

    private void addFather(Node father) {
        this.fathers.add(father);
    }

    public boolean hasFather(){
        return this.getFathers() != null;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setVisited() {
        this.visited = true;
    }

    public void setUnvisited() {
        this.visited = false;
    }

    public boolean isVisited() {
        return visited;
    }

    public boolean isAllChildrenVisited() {
        for (Node child : children){
            if (!child.isVisited()) {
                return false;
            }
        }
        return true;
    }

    public Node getMostLeftUnvisitedChild() {
        for(Node child: this.children){
            if(!child.isVisited()){
                return child;
            }
        }
        return null;
    }

    public void setNodeType(NodeTypes type) {
        nodeType = type;
    }
    public NodeTypes getNodeType(){
        return nodeType;
    }
}