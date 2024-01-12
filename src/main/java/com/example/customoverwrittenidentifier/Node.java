package com.example.customoverwrittenidentifier;

import java.util.ArrayList;
import java.util.List;

public class Node {

    private  boolean visited = false;

    private NodeTypes nodeType;

    public enum NodeTypes {
        Entry, METHOD, METHOD_EXIT, CONDITION, LOOP, LOOP_EXIT, SWITCH, SWITCH_EXIT, POINTER
    }

    private final String methodName;

    private List<Node> children = new ArrayList<>();

    private List<Node> unitedFathers = new ArrayList<>();

    private List<Node> innerLoops = new ArrayList<>();

    private List<Node> innerLoopsAndSwitches = new ArrayList<>();

    private List<Node> chainPotentialFunctions = new ArrayList<>();

    private Node mainFather;

    private List<Node> fathers = new ArrayList<>();

    public Node(String methodName, NodeTypes nodeType) {
        this.methodName = methodName;
        this.mainFather = this;
        this.setNodeType(nodeType);
        this.innerLoops = new ArrayList<>();
        this.innerLoopsAndSwitches = new ArrayList<>();
        // when ever a node is initialized, its mainFather will be itself by default. (*)
    }

    public void addChild(Node child) {
        children.add(child);
        if(unitedFathers.isEmpty()){
            child.addFather(this);
        }
        else {
            child.addFathers(unitedFathers);
        }

        if(this.nodeType == NodeTypes.POINTER) {
            this.getFathers().get(0).removeLastMemberOfChainPotentialFunctions();
        }
        else{
            child.innerLoops = new ArrayList<>(this.innerLoops);
            child.innerLoopsAndSwitches = new ArrayList<>(this.innerLoopsAndSwitches);
            child.chainPotentialFunctions = new ArrayList<>(this.chainPotentialFunctions);

            if (this.nodeType == NodeTypes.LOOP) {
                child.innerLoops.add(this);
                child.innerLoopsAndSwitches.add(this);
            } else if (this.nodeType == NodeTypes.LOOP_EXIT) {
                int mostInnerLoopIndex = innerLoops.size() - 1;
                child.innerLoops.remove(mostInnerLoopIndex);
                int mostInnerLoopOrSwitchIndex = child.innerLoopsAndSwitches.size() - 1;
                child.innerLoopsAndSwitches.remove(mostInnerLoopOrSwitchIndex);
            } else if (this.nodeType == NodeTypes.SWITCH) {
                child.innerLoopsAndSwitches.add(this);
            } else if (this.nodeType == NodeTypes.SWITCH_EXIT) {
                int mostInnerLoopOrSwitchIndex = child.innerLoopsAndSwitches.size() - 1;
                child.innerLoopsAndSwitches.remove(mostInnerLoopOrSwitchIndex);
            } else if (this.nodeType == NodeTypes.METHOD) {
                child.chainPotentialFunctions.add(this);
            } else if (this.nodeType == NodeTypes.METHOD_EXIT) {
                int lastMemberOfPotentialNestedFunctionsSequenceIndex = child.chainPotentialFunctions.size() - 1;
                child.chainPotentialFunctions.remove(lastMemberOfPotentialNestedFunctionsSequenceIndex);
            }
        }

        child.mainFather = this.mainFather;  // children will be introduced to their main father by their most recent father. (*)
    }

    //----------------------------------------------------------------------------------------------------------------------------//
    // The two statements (search this tag => (*) ) will ensure the program that any node knows the main father which is the      //
    // entry node. At the beginning, each node considers itself as the main father. However, if the node is added as child of     //
    // another node, it will be introduced to the main father by the most recent father. Even though it will not happen, if a     //
    // child sets the main father as its child, it will introduce the main father as the main father again.                       //
    //----------------------------------------------------------------------------------------------------------------------------//

    public void setUnitedFathers(List<Node> unitedFathers) {
        this.unitedFathers = unitedFathers;
    }

    public List<Node> getChildlessNodes(NodeTypes nodeType) {
        List<Node> childlessNodesList = new ArrayList<>();
        getChildlessNodesRecursive(this, childlessNodesList, nodeType);
        this.setAllChildrenUnvisited();
        return childlessNodesList;
    }

    private void getChildlessNodesRecursive(Node node, List<Node> childlessNodesList, NodeTypes targetType) {
        if (!node.isVisited()) {
            node.setVisited();

            if (node.hasChild()) {
                for (Node child : node.getChildren()) {
                    getChildlessNodesRecursive(child, childlessNodesList, targetType);
                }
            } else {
                if (node.getNodeType() == targetType) {
                    childlessNodesList.add(node);
                }
            }
        }
    }

    public List<Node> getChainPotentialFunctions() {
        return chainPotentialFunctions;
    }

    public void setChainPotentialFunctions(List<Node> chainPotentialFunctions) {
        this.chainPotentialFunctions = chainPotentialFunctions;
    }

    public void addToChainPotentialFunctions(Node chainPotentialFunction) {
        this.chainPotentialFunctions.add(chainPotentialFunction);
    }

    public void removeLastMemberOfChainPotentialFunctions() {
        int lastMemberOfChainPotentialFunctionsIndex = this.chainPotentialFunctions.size() - 1;
        this.chainPotentialFunctions.remove(lastMemberOfChainPotentialFunctionsIndex);
    }

    public boolean doesThisExitsInChainPotentialFunctions(){
        for(int i = chainPotentialFunctions.size() -1; i > 0; i--){
            Node chainPotentialFunction = chainPotentialFunctions.get(i);
            if(chainPotentialFunction.getMethodName().equals(this.getMethodName())){
                return true;
            }
        }
        return false;
    }

    public Node getTheOriginalFunctionNode(){
        for(int i = chainPotentialFunctions.size() -1; i > 0; i--){
            Node chainPotentialFunction = chainPotentialFunctions.get(i);
            if(chainPotentialFunction.getMethodName().equals(this.getMethodName())){
                return chainPotentialFunction;
            }
        }
        return null;
    }

    public Node getMostInnerLoop() {
        int mostInnerLoopIndex = innerLoops.size() - 1;
        return innerLoops.get(mostInnerLoopIndex);
    }

    public Node getExitPointOfMostInnerLoop() {
        int mostInnerLoopIndex = innerLoops.size() - 1;
        Node mostInnerLoopNode = innerLoops.get(mostInnerLoopIndex);

        List<Node> childrenOfMostInnerLoop = mostInnerLoopNode.getChildren();

        for (Node child : childrenOfMostInnerLoop){
            if(child.getNodeType() == NodeTypes.LOOP_EXIT){
                return child;
            }
        }
        return null;
    }

    public Node getMostInnerLoopsOrSwitch() {
        int mostInnerLoopIndex = innerLoopsAndSwitches.size() - 1;
        return innerLoopsAndSwitches.get(mostInnerLoopIndex);
    }

    public Node getExitPointOfMostInnerLoopsOrSwitch() {
        Node mostInnerLoopOrSwitchNode = this.getMostInnerLoopsOrSwitch();

        List<Node> childrenOfMostInnerLoop = mostInnerLoopOrSwitchNode.getChildren();

        for (Node child : childrenOfMostInnerLoop){
            if(child.getNodeType() == NodeTypes.SWITCH_EXIT){
                return child;
            }
            else if(child.getNodeType() == NodeTypes.LOOP_EXIT){
                return child;
            }
        }
        return null;
    }

    public List<Node> getSingleChildNodes(NodeTypes nodeType) {
        List<Node> singleChildNodesList = new ArrayList<>();
        getSingleChildNodesRecursive(this, singleChildNodesList, nodeType);
        this.setAllChildrenUnvisited();
        return singleChildNodesList;
    }

    private void getSingleChildNodesRecursive(Node node, List<Node> singleChildNodesList, NodeTypes targetType) {
        if (!node.isVisited()) {
            node.setVisited();

            if (node.hasChild()) {
                if (node.getNodeType() == targetType && node.getChildren().size() == 1) {
                    singleChildNodesList.add(node);
                }

                for (Node child : node.getChildren()) {
                    getSingleChildNodesRecursive(child, singleChildNodesList, targetType);
                }
            }
        }
    }

    public void setAllChildrenUnvisited() {
        setAllChildrenUnvisitedRecursive(this);
    }

    private void setAllChildrenUnvisitedRecursive(Node node) {
        node.setUnvisited();

        if (node.hasChild()) {
            for (Node child : node.getChildren()) {
                setAllChildrenUnvisitedRecursive(child);
            }
        }
    }




    public List<Node> getChildren() {
        return children;
    }

    public List<Node> getFathers() {
        return fathers;
    }

    public Node getMainFather(){
        return mainFather;
    }



    private void addFather(Node father) {
        this.fathers.add(father);
    }

    public void addFathers(List<Node> fathers) {
        this.fathers.addAll(fathers);
    }

    public boolean hasChild(){
        return this.getChildren() != null;
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