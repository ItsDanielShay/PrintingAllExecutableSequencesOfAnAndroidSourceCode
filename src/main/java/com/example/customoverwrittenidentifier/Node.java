package com.example.customoverwrittenidentifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Node {

    private boolean visited = false;

    private NodeTypes nodeType;

    public enum NodeTypes {
        ENTRY, METHOD, METHOD_EXIT, CONDITION, LOOP, LOOP_EXIT,
        SWITCH, CASE, SWITCH_EXIT, POINTER, POINTER_AFFILIATION,
        TRY, CATCH, FINALLY, BREAK, CONTINUE, ALL,
        STATEMENT // <--- For unrecognized or generic statements
    }

    private final String name;

    public boolean isMethodRecursive() {
        return isMethodRecursive;
    }

    public void setMethodRecursive(boolean methodRecursive) {
        isMethodRecursive = methodRecursive;
    }

    private boolean isMethodRecursive = false;

    public boolean isMethodFirstMemberOfMultipleFunctions() {
        return isMethodFirstMemberOfMultipleFunctions;
    }

    public void setMethodFirstMemberOfMultipleFunctions(boolean methodFirstMemberOfMultipleFunctions) {
        isMethodFirstMemberOfMultipleFunctions = methodFirstMemberOfMultipleFunctions;
    }

    private boolean isMethodFirstMemberOfMultipleFunctions = false;
    private List<Node> children = new ArrayList<>();

    private List<Node> unitedFathers = new ArrayList<>();

    private List<Node> innerLoops = new ArrayList<>();

    private List<Node> innerLoopsAndSwitches = new ArrayList<>();

    private List<Node> chainPotentialFunctions = new ArrayList<>();

    public List<Node> getPointersChain() {
        return pointersChain;
    }

    public void setPointersChain(List<Node> pointersChain) {
        this.pointersChain = pointersChain;
    }

    private List<Node> pointersChain = new ArrayList<>();

    private Node mainFather;

    public Node getPartner() {
        return partner;
    }

    public void setPartner(Node pointerPartner) {
        this.partner = pointerPartner;
    }

    private Node partner = null;

    public int getPointerCounts() {
        return pointerCounts;
    }

    public void setPointerCounts(int pointerCounts) {
        this.pointerCounts = pointerCounts;
    }


    // this gets the pointers enough information about how many times the affiliation pointer should keep the sequence generator in loop,
    // although sequence generator is not exits yet, addOneToPointerCount function will be useful for it when it is traversing a completed CCFG.
    public void addOneToPointerCount(){
        pointerCounts++;
        this.getPartner().pointerCounts++;
    }

    private int pointerCounts = 0;

    public Node getVeryNextStatementNodeOfLatterPartOfAMethod() {
        return veryNextStatementNodeOfLatterPartOfAMethod;
    }

    public void setVeryNextStatementNodeOfLatterPartOfAMethod(Node veryNextStatementOfLatterPartOfAMethod) {
        this.veryNextStatementNodeOfLatterPartOfAMethod = veryNextStatementOfLatterPartOfAMethod;
    }

    // this is just
    private Node veryNextStatementNodeOfLatterPartOfAMethod = null;

    private List<Node> fathers = new ArrayList<>();

    public void introduceToPartner(Node affiliationNode){
        affiliationNode.setPartner(this);
        this.setPartner(affiliationNode);
    }

    public Node(String name, NodeTypes nodeType) {
        this.name = name;
        this.mainFather = this;
        this.setNodeType(nodeType);
        this.innerLoops = new ArrayList<>();
        this.innerLoopsAndSwitches = new ArrayList<>();
        this.chainPotentialFunctions = new ArrayList<>();
        this.pointersChain = new ArrayList<>();
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

        boolean childIsNewNode = child.getChildren().isEmpty();

        if(childIsNewNode) {
            child.innerLoops = new ArrayList<>(this.innerLoops);
            child.innerLoopsAndSwitches = new ArrayList<>(this.innerLoopsAndSwitches);
            child.chainPotentialFunctions = new ArrayList<>(this.chainPotentialFunctions);
            child.pointersChain = new ArrayList<>(this.pointersChain);

            if (this.nodeType == NodeTypes.LOOP) {
                child.innerLoops.add(this);
                child.innerLoopsAndSwitches.add(this);
            } else if (this.nodeType == NodeTypes.LOOP_EXIT) {
                int mostInnerLoopIndex = innerLoops.size() - 1;

                innerLoops.get(mostInnerLoopIndex).introduceToPartner(child);

                child.innerLoops.remove(mostInnerLoopIndex);
                int mostInnerLoopOrSwitchIndex = child.innerLoopsAndSwitches.size() - 1;
                child.innerLoopsAndSwitches.remove(mostInnerLoopOrSwitchIndex);
            } else if (this.nodeType == NodeTypes.SWITCH) {
                child.innerLoopsAndSwitches.add(this);
            } else if (this.nodeType == NodeTypes.SWITCH_EXIT) {
                int mostInnerLoopOrSwitchIndex = child.innerLoopsAndSwitches.size() - 1;

                innerLoopsAndSwitches.get(mostInnerLoopOrSwitchIndex).introduceToPartner(child);

                child.innerLoopsAndSwitches.remove(mostInnerLoopOrSwitchIndex);
            } else if (this.nodeType == NodeTypes.METHOD) {
                child.chainPotentialFunctions.add(this);
            } else if (this.nodeType == NodeTypes.METHOD_EXIT) {

                getLastMemberOfChainPotentialFunctions().introduceToPartner(child);

                child.removeLastMemberOfChainPotentialFunctions();
            }
            else if(this.nodeType == NodeTypes.POINTER){
                child.pointersChain.add(this);
            }
            else if(this.nodeType == NodeTypes.POINTER_AFFILIATION){
                int lastPointerIndex = innerLoops.size() - 1;

                pointersChain.get(lastPointerIndex).introduceToPartner(child);

                child.pointersChain.remove(lastPointerIndex);
            }
        }

        child.mainFather = this.mainFather;  // children will be introduced to their main father by their most recent father. (*)
    }

    public void removeChild(Node child){
        children.remove(child);
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

    // each node may have an exit node, and we don't want to get it as one of the childless nodes, so it is excluded from the list, if it exits.
    public List<Node> getChildlessNodes(NodeTypes nodeType) {
        Node exitPointOfThis = null;
        for(Node child :this.getChildren()){
            if(child.getNodeType() == NodeTypes.LOOP_EXIT || child.getNodeType() == NodeTypes.SWITCH_EXIT || child.getNodeType() == NodeTypes.METHOD_EXIT) {
                exitPointOfThis = child;
            }
        }
        List<Node> childlessNodesList = new ArrayList<>();
        getChildlessNodesRecursive(this, childlessNodesList, nodeType);
        this.setAllChildrenUnvisited();
        if(exitPointOfThis != null){
            childlessNodesList.remove(exitPointOfThis);
        }
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
                if(targetType == NodeTypes.ALL){
                    childlessNodesList.add(node);
                }
                else{
                    if (node.getNodeType() == targetType) {
                        childlessNodesList.add(node);
                    }
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

    public Node getLastMemberOfChainPotentialFunctions() {
        int lastMemberOfChainPotentialFunctionsIndex = this.chainPotentialFunctions.size() - 1;
        return this.chainPotentialFunctions.get(lastMemberOfChainPotentialFunctionsIndex);
    }

    public boolean doesThisExitsInChainPotentialFunctions(){
        for(int i = chainPotentialFunctions.size() -1; i > 0; i--){
            Node chainPotentialFunction = chainPotentialFunctions.get(i);
            if(chainPotentialFunction.getName().equals(this.getName())){
                return true;
            }
        }
        return false;
    }

    public Node getTheOriginalFunctionNode(){
        for(int i = chainPotentialFunctions.size() -1; i > 0; i--){
            Node chainPotentialFunction = chainPotentialFunctions.get(i);
            if(chainPotentialFunction.getName().equals(this.getName())){
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


    // visitedNodes map is used because the main point of the method 'setAllChildrenUnvisited' is to set them unvisited,
    // to do so, we need a map to keep the track of visited method using it.
    Map<Node, Boolean> visitedNodes = new HashMap<>();
    public void setAllChildrenUnvisited() {
        setAllChildrenUnvisitedRecursive(this);
        visitedNodes.clear();
    }

    private void setAllChildrenUnvisitedRecursive(Node node) {
        if(!visitedNodes.containsKey(node)){
            visitedNodes.put(node, true);
            node.setUnvisited();

            if (node.hasChild()) {
                for (Node child : node.getChildren()) {
                    setAllChildrenUnvisitedRecursive(child);
                }
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

    public String getName() {
        return name;
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