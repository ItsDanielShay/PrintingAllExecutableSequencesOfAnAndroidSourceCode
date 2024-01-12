package com.example.customoverwrittenidentifier;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;


public class buildCallControlFlowGraph extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        List<Node> callControlFlowGraphs = new ArrayList<>();
        // Retrieve the editor and PSI file from the action event
        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);


        // Check if the editor and PSI file are available
        if (editor == null || psiFile == null) {
            return;
        }

        // Perform a global search for all classes in the project
        AllClassesSearch.search(GlobalSearchScope.projectScope(Objects.requireNonNull(editor.getProject())), editor.getProject()).forEach(psiClass -> {

            // Visit each class and its methods
            psiClass.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitMethod(PsiMethod method) {
                    super.visitMethod(method);

                    // Check if the method is annotated with @Override
                    if (method.getAnnotation("java.lang.Override") != null) {
                        Node entryNode = new Node("Entry Node", Node.NodeTypes.Entry);
                        String overriddenMethodName = method.getName();
                        Node overriddenMethodNode = new Node(overriddenMethodName, Node.NodeTypes.METHOD);
                        entryNode.addChild(overriddenMethodNode);
                        callControlFlowGraphs.add(overriddenMethodNode);
                        raiseTheTree(method, editor.getProject().getBasePath(), callControlFlowGraphs);
                    }
                }
            });
            // Return true to continue iterating through classes
            return true;
        });
        // Display the results in a dialog

        callControlFlowGraphs.replaceAll(Node::getMainFather);

        showDialog(editor.getProject());
    }

    private void raiseTheTree(PsiMethod psiMethod, String basePath, List<Node> callControlFlowGraphs) {
        // Check if the method is within the project's base path
        if (psiMethod.getContainingFile().getVirtualFile().getPath().startsWith(basePath)) {
            // Check if the method is annotated with @Override
            PsiAnnotation overrideAnnotation = psiMethod.getModifierList().findAnnotation("java.lang.Override");
            if (overrideAnnotation != null ) {
                PsiCodeBlock body = psiMethod.getBody();
                if (body != null) {

                    // Retrieve statements from the method body
                    PsiStatement[] statements = body.getStatements();

                    // Iterate through statements in the method body
                    for (PsiStatement statement : statements) {
//                        String lastMethod;
//                        lastMethod = psiMethod.getName();
//                        StringBuilder stringBuilder = new StringBuilder(lastMethod);

                        controlFlowStatementsIdentifier(statement, basePath, psiMethod, callControlFlowGraphs);
                    }
                }
            }
        }
    }


    private void controlFlowStatementsIdentifier(PsiStatement statement, String basePath, PsiMethod method, List<Node> callControlFlowGraphs) {


            // Get the type of the statement (If, For, Try, Switch, While, Do While, MethodCall, Declaration)
            String statementType = getStatementType(statement);

            // Perform actions based on the type of statement
            switch (statementType) {
                case "If":
                    processIfStatement((PsiIfStatement) statement, method, basePath, callControlFlowGraphs, null);
                    break;
                case "For":
                    processForStatement((PsiForStatement) statement, method, basePath, callControlFlowGraphs);
                    break;
                case "Try":
                    processTryStatement((PsiTryStatement) statement, method, basePath, callControlFlowGraphs);
                    break;
                case "Switch":
                    processSwitchStatement((PsiSwitchStatement) statement, method, basePath, callControlFlowGraphs);
                    break;
                case "While":
                    processWhileStatement((PsiWhileStatement) statement, method, basePath, callControlFlowGraphs);
                    break;
                case "Do While":
                    processDoWhileStatement((PsiDoWhileStatement) statement, method, basePath, callControlFlowGraphs);
                    break;
                case "MethodCall":
                    processMethodStatement((PsiExpressionStatement) statement, basePath, callControlFlowGraphs);
                    break;
                case "Declaration":
                    processDeclarationStatement((PsiDeclarationStatement) statement, callControlFlowGraphs);
                    break;
                case "Break":
                    processBreakStatement(callControlFlowGraphs);
                    break;
                case "Continue":
                    processContinueStatement(callControlFlowGraphs);
                    break;
            }
    }

    public String getStatementType(PsiStatement statement){
        // Check the class of the PsiStatement to identify its type
        if(statement instanceof PsiForStatement){
            return "For";
        }
        else if(statement instanceof PsiIfStatement){
            return "If";
        }
        else if(statement instanceof PsiWhileStatement){
            return "While";
        }
        else if(statement instanceof PsiDoWhileStatement){
            return "Do While";
        }
        else if(statement instanceof PsiSwitchStatement){
            return "Switch";
        }
        else if(statement instanceof PsiTryStatement){
            return "Try";
        }
        else if(statement instanceof PsiExpressionStatement && ((PsiExpressionStatement) statement).getExpression() instanceof PsiMethodCallExpression){
            return "MethodCall";
        }
        else if(statement instanceof PsiDeclarationStatement){
            return "Declaration";
        }
        else if(statement instanceof PsiBreakStatement){
            return "Break";
        }
        else if(statement instanceof PsiContinueStatement){
            return "Continue";
        }
        else if(statement instanceof PsiCodeBlock){
            return "Code Block";
        }
        else if(statement instanceof PsiBlockStatement){
            return "Block Statement";
        }
        else if(statement instanceof PsiLoopStatement){
            return "Loop Statement";
        }
        // Return "Not identified" if the statement type is not recognized
        return "Not identified";
    }

    private void processForStatement(PsiForStatement forStatement, PsiMethod method, String basePath, List<Node> callControlFlowGraphs) {
        // Get the body of the for statement
        PsiStatement thenBranch = forStatement.getBody();
        assert thenBranch != null;

        String condition = Objects.requireNonNull(forStatement.getCondition()).getText();
        Node forStatementNode = new Node(condition, Node.NodeTypes.LOOP);


        int lastNodeIndex = callControlFlowGraphs.size()-1;
        callControlFlowGraphs.get(lastNodeIndex).addChild(forStatementNode);

        Node exitPointOfforStatementNode = new Node("Loop Exit Point", Node.NodeTypes.LOOP_EXIT);
        forStatementNode.addChild(exitPointOfforStatementNode);

        callControlFlowGraphs.set(lastNodeIndex, forStatementNode);

        // Iterate through statements in the body of the for statement and identify control flow statements
        for (PsiStatement innerStatement : PsiTreeUtil.findChildrenOfType(thenBranch, PsiStatement.class)) {
            // Recursively analyze control flow statements within the body of the for statement
            controlFlowStatementsIdentifier(innerStatement, basePath, method, callControlFlowGraphs);
        }

        callControlFlowGraphs.get(callControlFlowGraphs.size() - 1).addChild(forStatementNode);

        callControlFlowGraphs.set(lastNodeIndex, exitPointOfforStatementNode);
    }

    private void processIfStatement(PsiIfStatement ifStatement, PsiMethod method, String basePath, List<Node> callControlFlowGraphs, Node inputIfStatementNode) {
        // Get the then branch of the if statement
        PsiStatement thenBranch = ifStatement.getThenBranch();
        assert thenBranch != null;

        String condition = Objects.requireNonNull(ifStatement.getCondition()).getText();
        Node ifStatementNode = new Node(condition, Node.NodeTypes.CONDITION);
        int lastNodeIndex = callControlFlowGraphs.size() - 1;

        if(inputIfStatementNode == null) {
            callControlFlowGraphs.get(lastNodeIndex).addChild(ifStatementNode);
        }
        else {
            inputIfStatementNode.addChild(ifStatementNode);
        }

        callControlFlowGraphs.set(lastNodeIndex, ifStatementNode);

        // Iterate through statements in the then branch and identify control flow statements
        for (PsiStatement innerStatement : PsiTreeUtil.findChildrenOfType(thenBranch, PsiStatement.class)) {
            // Recursively analyze control flow statements within the then branch
            controlFlowStatementsIdentifier(innerStatement, basePath, method, callControlFlowGraphs);
        }

        // Check for the existence of the "else" branch
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != null) {
            // Iterate through statements in the else branch and identify control flow statements
            if(getStatementType((PsiStatement) elseBranch).equalsIgnoreCase("If")){
                processIfStatement((PsiIfStatement) elseBranch, method, basePath, callControlFlowGraphs, ifStatementNode);
            }
            else {
                Node elseStatementNode = new Node("Else", Node.NodeTypes.CONDITION);
                ifStatementNode.addChild(elseStatementNode);

                callControlFlowGraphs.set(lastNodeIndex, elseStatementNode);

                for (PsiStatement innerStatement : PsiTreeUtil.findChildrenOfType(elseBranch, PsiStatement.class)) {
                    // Recursively analyze control flow statements within the else branch
                    controlFlowStatementsIdentifier(innerStatement, basePath, method, callControlFlowGraphs);
                }

                makeIfLeafNodesUnited(callControlFlowGraphs, ifStatementNode, lastNodeIndex);
            }
        }
        else {
            makeIfLeafNodesUnited(callControlFlowGraphs, ifStatementNode, lastNodeIndex);
        }
    }

    private void makeIfLeafNodesUnited(List<Node> callControlFlowGraphs, Node ifStatementNode, int lastNodeIndex) {
        List<Node> unitedFathers = new ArrayList<>();
        List<Node> childlessMethodNodes = ifStatementNode.getChildlessNodes(Node.NodeTypes.METHOD);
        List<Node> childlessConditionNodes = ifStatementNode.getChildlessNodes(Node.NodeTypes.CONDITION);
        List<Node> singleChildConditionNodes = ifStatementNode.getSingleChildNodes(Node.NodeTypes.CONDITION);

        unitedFathers.addAll(childlessMethodNodes);
        unitedFathers.addAll(childlessConditionNodes);
        unitedFathers.addAll(singleChildConditionNodes);

        callControlFlowGraphs.get(lastNodeIndex).setUnitedFathers(unitedFathers);
    }

    private void processWhileStatement(PsiWhileStatement whileStatement, PsiMethod method, String basePath, List<Node> callControlFlowGraphs) {
        // Get the body of the while statement
        PsiStatement thenBranch = whileStatement.getBody();
        assert thenBranch != null;

        String condition = Objects.requireNonNull(whileStatement.getCondition()).getText();
        Node whileStatementNode = new Node(condition, Node.NodeTypes.LOOP);
        addNodeToGraph(callControlFlowGraphs, whileStatementNode);

        Node exitPointOfWhileStatementNode = new Node("Loop Exit", Node.NodeTypes.LOOP_EXIT);

        whileStatementNode.addChild(exitPointOfWhileStatementNode);

        Iterable<PsiStatement> statements = Lists.newArrayList(whileStatement.getBody());
        statements.forEach(statement -> {
            controlFlowStatementsIdentifier(statement, basePath, method, callControlFlowGraphs);
        });

        int lastNodeIndex = callControlFlowGraphs.size() - 1;

        // Add a link from the last node inside the while block to the condition node
        callControlFlowGraphs.get(lastNodeIndex).addChild(whileStatementNode);

        // Update the last node to be the while statement node
        callControlFlowGraphs.set(lastNodeIndex, exitPointOfWhileStatementNode);
    }

    private void processDoWhileStatement(PsiDoWhileStatement doWhileStatement, PsiMethod method, String basePath, List<Node> callControlFlowGraphs) {
        // Get the body of the do-while statement
        PsiStatement thenBranch = doWhileStatement.getBody();
        assert thenBranch != null;

        // Build a call sequence indicating the start of a do-while loop
        String condition = Objects.requireNonNull(doWhileStatement.getCondition()).getText();
        Node doWhileStatementNode = new Node(condition, Node.NodeTypes.CONDITION);
        int lastNodeIndex = callControlFlowGraphs.size()-1;

        Node doBlockNode = new Node("Do Block", Node.NodeTypes.LOOP);
        
        addNodeToGraph(callControlFlowGraphs, doBlockNode);

        Node exitPointOfDoWhileStatementNode = new Node("Loop Exit", Node.NodeTypes.LOOP_EXIT);

        PsiCodeBlock block = ((PsiBlockStatement) thenBranch).getCodeBlock();

        for(PsiStatement statement : block.getStatements()){
            controlFlowStatementsIdentifier(statement, basePath, method, callControlFlowGraphs);
        }

        callControlFlowGraphs.get(lastNodeIndex).addChild(doWhileStatementNode);

        doWhileStatementNode.addChild(exitPointOfDoWhileStatementNode);

        callControlFlowGraphs.set(lastNodeIndex, exitPointOfDoWhileStatementNode);
    }

    private void processSwitchStatement(PsiSwitchStatement switchStatement, PsiMethod method, String basePath, List<Node> callControlFlowGraphs) {
        // Check if the switch statement has a body (non-empty case blocks)
        boolean lastCaseHadBreak = false;
        
        int lastNodeIndex = callControlFlowGraphs.size()-1;
        Node switchStatementNode = new Node("Switch", Node.NodeTypes.SWITCH);
        
        addNodeToGraph(callControlFlowGraphs, switchStatementNode);

        Node exitPointOfSwitchStatementNode = new Node("Switch Exit", Node.NodeTypes.SWITCH_EXIT);
        switchStatementNode.addChild(exitPointOfSwitchStatementNode);

        List<Node> nonBreakNodes = new ArrayList<>();

        Node previousCaseNode = null;
        if (switchStatement.getBody() != null) {
            for (PsiStatement innerStatement : switchStatement.getBody().getStatements()) {
                if (innerStatement instanceof PsiSwitchLabelStatement) {
                    PsiSwitchLabelStatement switchLabelStatement = (PsiSwitchLabelStatement) innerStatement;

                    if (switchLabelStatement.isDefaultCase()) {
                        String caseCondition = "Default";
                        Node caseConditionStatementNode = new Node(caseCondition, Node.NodeTypes.CONDITION);

                        if (previousCaseNode == null){
                            callControlFlowGraphs.get(lastNodeIndex).addChild(caseConditionStatementNode);
                        }
                        else {
                            previousCaseNode.addChild(caseConditionStatementNode);
                        }

                        previousCaseNode = caseConditionStatementNode;

                        callControlFlowGraphs.set(lastNodeIndex, caseConditionStatementNode);
                    } else {
                        
                        for (PsiCaseLabelElement caseLabelElement : Objects.requireNonNull(switchLabelStatement.getCaseLabelElementList()).getElements()) {

                            String caseCondition = caseLabelElement.getText();
                            Node caseConditionStatementNode = new Node(caseCondition, Node.NodeTypes.CONDITION);

                            if (previousCaseNode == null){
                                switchStatementNode.addChild(caseConditionStatementNode);
                            }
                            else {
                                if(!lastCaseHadBreak){
                                    nonBreakNodes.add(previousCaseNode);
                                }
                                if (lastCaseHadBreak) {
                                    lastCaseHadBreak = false;
                                    if (nonBreakNodes.isEmpty()) {
                                        switchStatementNode.addChild(caseConditionStatementNode);
                                    } else {
                                        Node mostLastNonBreakNode = nonBreakNodes.get(nonBreakNodes.size() - 1);
                                        mostLastNonBreakNode.addChild(caseConditionStatementNode);

                                        switchStatementNode.addChild(caseConditionStatementNode);
                                    }
                                } else {
                                    switchStatementNode.addChild(caseConditionStatementNode);

                                    Node mostLastNonBreakNode = nonBreakNodes.get(nonBreakNodes.size() - 1);
                                    mostLastNonBreakNode.addChild(caseConditionStatementNode);
                                }
                            }

                            previousCaseNode = caseConditionStatementNode;

                            callControlFlowGraphs.set(lastNodeIndex, caseConditionStatementNode);
                        }
                    }
                }
                else {
                    controlFlowStatementsIdentifier(innerStatement, basePath, method, callControlFlowGraphs);
                }

                if(getStatementType(innerStatement).equals("Break")){
                    lastCaseHadBreak = true;
                }
            }
        }
        
        // break statements are connected to the exitPointOfSwitchStatementNode automatically using processBreakStatements function
        callControlFlowGraphs.get(lastNodeIndex).addChild(exitPointOfSwitchStatementNode);

        callControlFlowGraphs.set(lastNodeIndex, exitPointOfSwitchStatementNode);
    }

    private void processTryStatement(PsiTryStatement tryStatement, PsiMethod method, String basePath, List<Node> callControlFlowGraphs) {
        // Get the try block of the try statement
        PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        assert tryBlock != null;

        int lastNodeIndex = callControlFlowGraphs.size()-1;
        Node initialFather = callControlFlowGraphs.get(lastNodeIndex).getFathers().get(0);

        String condition = "Try Block";
        Node tryStatementNode = new Node(condition, Node.NodeTypes.CONDITION);

        Node parentNode = callControlFlowGraphs.get(lastNodeIndex);

        addNodeToGraph(callControlFlowGraphs, tryStatementNode);

        // Iterate through statements in the try block and identify control flow statements
        for (PsiStatement tryStatementInner : PsiTreeUtil.findChildrenOfType(tryBlock, PsiStatement.class)) {
            controlFlowStatementsIdentifier(tryStatementInner, basePath, method, callControlFlowGraphs);
        }

        Node lastTryNode = callControlFlowGraphs.get(lastNodeIndex);

        callControlFlowGraphs.set(lastNodeIndex, initialFather);

        String catchCondition = "Catch Block";
        Node catchStatementNode = new Node(catchCondition, Node.NodeTypes.CONDITION);
        
        parentNode.addChild(catchStatementNode);
        callControlFlowGraphs.set(lastNodeIndex, catchStatementNode);

        // Get catch blocks from the try statement
        PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for (PsiCodeBlock catchBlock : catchBlocks) {
            for(PsiStatement statement : catchBlock.getStatements()){
                controlFlowStatementsIdentifier(statement, basePath, method, callControlFlowGraphs);
            }
        }

        Node lastCatchNode = callControlFlowGraphs.get(lastNodeIndex);

        // Get the finally block from the try statement
        PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
            callControlFlowGraphs.set(lastNodeIndex, initialFather);
            String finallyCondition = "Finally Block";
            Node finallyStatementNode = new Node(finallyCondition, Node.NodeTypes.CONDITION);

            lastTryNode.addChild(finallyStatementNode);
            lastCatchNode.addChild(finallyStatementNode);

            callControlFlowGraphs.set(lastNodeIndex, finallyStatementNode);

            processFinallyBlock(finallyBlock, method, basePath, callControlFlowGraphs);
        }
        else {
            List<Node> unitedFathers = new ArrayList<>();
            unitedFathers.add(lastTryNode);
            unitedFathers.add(lastCatchNode);

            lastCatchNode.setUnitedFathers(unitedFathers);
            lastTryNode.setUnitedFathers(unitedFathers);
        }
    }

    private void processFinallyBlock(PsiCodeBlock finallyBlock, PsiMethod method, String basePath, List<Node> callControlFlowGraphs) {
        // Create a StringBuilder for the child call sequence, indicating the start of a "Finally" block

        // Iterate through statements in the finally block and identify control flow statements
        for (PsiStatement finallyStatement : PsiTreeUtil.findChildrenOfType(finallyBlock, PsiStatement.class)) {
            // Recursively analyze control flow statements within the finally block
            controlFlowStatementsIdentifier(finallyStatement, basePath, method, callControlFlowGraphs);
        }
    }

    private void processMethodStatement(PsiExpressionStatement callControlFlowGraphStatement, String basePath, List<Node> callControlFlowGraphs) {
        // Resolve the method called in the expression
        PsiMethod resolvedMethod = ((PsiMethodCallExpression) callControlFlowGraphStatement.getExpression()).resolveMethod();

        if (resolvedMethod != null) {

            String methodName = resolvedMethod.getName();
            Node methodNode = new Node(methodName, Node.NodeTypes.METHOD);
            int lastNodeIndex = callControlFlowGraphs.size()-1;
            callControlFlowGraphs.get(lastNodeIndex).addChild(methodNode);

            callControlFlowGraphs.set(lastNodeIndex, methodNode);

            // Check if the method is within the project's base path
            if (resolvedMethod.getContainingFile().getVirtualFile().getPath().startsWith(basePath)) {
                // Analyze the method's body for control flow statements
                PsiCodeBlock body = resolvedMethod.getBody();
                if (body != null) {

                    for (PsiStatement statement : body.getStatements()) {
                        // Recursively analyze control flow statements within the method's body
                        if(getStatementType(statement).equals("MethodCall")){

                            PsiExpressionStatement innerStatementExpression = (PsiExpressionStatement) statement;
                            PsiMethod resolvedInnerMethod = ((PsiMethodCallExpression) innerStatementExpression.getExpression()).resolveMethod();
                            if(resolvedInnerMethod != null){
                                String innerMethodName = resolvedInnerMethod.getName();
                                Node innerMethodNode = new Node(innerMethodName, Node.NodeTypes.METHOD);

                                innerMethodNode.setChainPotentialFunctions(methodNode.getChainPotentialFunctions());
                                innerMethodNode.addToChainPotentialFunctions(innerMethodNode);

                                // if this method-statement will build a loop of methods, points out to the original method node
                                // which is the first member of the chain. Covers both recursive and multiple functions.
                                if(innerMethodNode.doesThisExitsInChainPotentialFunctions()){
                                    Node terminalNodeOfTheNestedFunctions = innerMethodNode.getTheOriginalFunctionNode();

                                    Node pointerNode = new Node("Pointer", Node.NodeTypes.POINTER);
                                    Node lastNode = callControlFlowGraphs.get(lastNodeIndex);
                                    lastNode.addChild(pointerNode);
                                    pointerNode.addChild(terminalNodeOfTheNestedFunctions);

                                    callControlFlowGraphs.set(lastNodeIndex, pointerNode);
                                }
                                else {
                                    controlFlowStatementsIdentifier(statement, basePath, resolvedMethod, callControlFlowGraphs);
                                }
                            }
                            else {
                                controlFlowStatementsIdentifier(statement, basePath, resolvedMethod, callControlFlowGraphs);
                            }
                        }
                        else {
                            controlFlowStatementsIdentifier(statement, basePath, resolvedMethod, callControlFlowGraphs);
                        }
                    }
                }
            }

            Node exitPointMethodNode = new Node(methodName, Node.NodeTypes.METHOD_EXIT);
            addNodeToGraph(callControlFlowGraphs, exitPointMethodNode);
        }
    }


    private void processDeclarationStatement(PsiDeclarationStatement statement, List<Node> callControlFlowGraphs) {
        // Get the declared elements in the declaration statement
        PsiElement[] declaredElements = statement.getDeclaredElements();

        // Iterate through the declared elements
        for (PsiElement declaredElement : declaredElements) {
            // Check if the declared element is a variable with an initializer
            if (declaredElement instanceof PsiVariable) {
                PsiVariable variable = (PsiVariable) declaredElement;

                // Get the initializer expression
                PsiExpression initializer = variable.getInitializer();

                // Check if the initializer is a method call expression
                if (initializer instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) initializer;

                    PsiReferenceExpression methodReference = methodCallExpression.getMethodExpression();

                    String methodName = methodReference.getReferenceName();
                    Node methodNode = new Node(methodName, Node.NodeTypes.METHOD);

                    addNodeToGraph(callControlFlowGraphs, methodNode);
                }
            }
        }
    }

    private void processBreakStatement(List<Node> callControlFlowGraphs) {
        Node breakNode = new Node("Break", Node.NodeTypes.CONDITION);

        int lastIndex = callControlFlowGraphs.size()-1;
        Node lastNode = callControlFlowGraphs.get(lastIndex);
        lastNode.addChild(breakNode);

        Node exitPointOfMostInnerLoop = breakNode.getExitPointOfMostInnerLoopsOrSwitch();

        if (exitPointOfMostInnerLoop != null) {
            breakNode.addChild(exitPointOfMostInnerLoop);
        }
    }

    private void processContinueStatement(List<Node> callControlFlowGraphs) {
        Node continueNode = new Node("Continue", Node.NodeTypes.CONDITION);

        int lastIndex = callControlFlowGraphs.size()-1;
        Node lastNode = callControlFlowGraphs.get(lastIndex);
        lastNode.addChild(continueNode);

        Node mostInnerLoop = continueNode.getMostInnerLoop();

        if (mostInnerLoop != null) {
            continueNode.addChild(mostInnerLoop);
        } else {
            // Handle the case when mostInnerLoop is null (potential issue)
            System.out.println("Error: mostInnerLoop is null. (causing an error at execution time too)");
        }
    }

    public void addNodeToGraph(List<Node> callControlFlowGraphs, Node newNode){
        int lastNodeIndex = callControlFlowGraphs.size() - 1;
        callControlFlowGraphs.get(lastNodeIndex).addChild(newNode);
        callControlFlowGraphs.set(lastNodeIndex, newNode);
    }

    private void showDialog(Project project) {
        String title = "Information";

        String message = "The Call Control Flow Graphs of the given Android source code has been built." +
                " Yet, to print them here, a function is needed to print string patterns, representing all sequences that can have" +
                " infinite cases and print the rest sequences that are certain. The code has been written using OOP, so as the graphs" +
                " can be changed easily, developers can apply their thoughts and ideas into them with least effort.";

        // Display the dialog
        Messages.showMessageDialog(project, message, title, Messages.getInformationIcon());
    }
    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        e.getPresentation().setEnabled(editor != null && psiFile != null);
    }
}