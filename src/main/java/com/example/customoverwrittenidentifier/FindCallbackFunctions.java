package com.example.customoverwrittenidentifier;

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

public class FindCallbackFunctions extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        List<String> methodCalls = new ArrayList<>();
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
                            //Print every branch of a tree with the method as its root.
                            printTree(method, editor.getProject().getBasePath(), methodCalls);
                    }
                }
            });
            // Return true to continue iterating through classes
            return true;
        });
        // Display the results in a dialog
        showDialog(editor.getProject(), methodCalls);
    }

    /**
     * Recursively traverses the call graph of a given method, analyzing its body for control flow statements.
     * If the method is within the specified project's base path and annotated with @Override, the call graph
     * is explored to identify and record call sequences using the "controlFlowStatementsIdentifier" function.
     *
     * @param psiMethod            The PsiMethod to be analyzed.
     * @param basePath             The base path of the project.
     * @param methodCalls          A list that preserves the identified sequences.
     */
    private void printTree(PsiMethod psiMethod, String basePath, List<String> methodCalls) {
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
                        String lastMethod;
                        lastMethod = psiMethod.getName();
                        StringBuilder stringBuilder = new StringBuilder(lastMethod);



                        // Set to keep track of processed statements and avoid recursion
                        Set<PsiStatement> processedStatements = new HashSet<>();

                        /*
                          Analyzes control flow statements within the method body. If further tasks require a more sophisticated
                          analysis of each flow statement, I suggest to modify this function to construct items in a form of
                          object-oriented tree-based structure. To do so, I developed a pre-version with the name "Node" class
                          that facilitates the representation
                          and manipulation of the CFG.
                         */
                        controlFlowStatementsIdentifier(statement, basePath, psiMethod, stringBuilder, processedStatements, methodCalls);
                    }
                }
            }
        }
    }


    /**
     * The core component for analyzing and Identifying various types of control flow statements within a method.
     * The main purpose of this function is to recursively traverse the Control Flow Graph of a method.
     *
     * @param statement             The PsiStatement to be analyzed.
     * @param basePath              The base path of the project.
     * @param method                The PsiMethod containing the statement.
     * @param stringBuilder         A StringBuilder to build the call sequence.
     * @param processedStatements   A Set to keep track of processed statements.
     * @param methodCalls           A list that preserves the identified sequences.
     */
    private void controlFlowStatementsIdentifier(PsiStatement statement, String basePath, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, List<String> methodCalls) {
        // Check if the statement has not been processed to avoid recursion
        if (!processedStatements.contains(statement)) {
            processedStatements.add(statement);

            // Get the type of the statement (If, For, Try, Switch, While, Do While, MethodCall, Declaration)
            String statementType = getStatementType(statement);

            // Perform actions based on the type of statement
            switch (statementType) {
                case "If":
                    processIfStatement((PsiIfStatement) statement, method, stringBuilder, processedStatements, basePath, methodCalls);
                    break;
                case "For":
                    processForStatement((PsiForStatement) statement, method, stringBuilder, processedStatements, basePath, methodCalls);
                    break;
                case "Try":
                    processTryStatement((PsiTryStatement) statement, method, stringBuilder, processedStatements, basePath, methodCalls);
                    break;
                case "Switch":
                    processSwitchStatement((PsiSwitchStatement) statement, method, stringBuilder, processedStatements, basePath, methodCalls);
                    break;
                case "While":
                    processWhileStatement((PsiWhileStatement) statement, method, stringBuilder, processedStatements, basePath, methodCalls);
                    break;
                case "Do While":
                    processDoWhileStatement((PsiDoWhileStatement) statement, method, stringBuilder, processedStatements, basePath, methodCalls);
                    break;
                case "MethodCall":
                    processMethodCallStatement((PsiExpressionStatement) statement, stringBuilder, processedStatements, basePath, methodCalls);
                    break;
                case "Declaration":
                    processDeclarationStatement((PsiDeclarationStatement) statement, stringBuilder, methodCalls);
                    break;
            }
        }
    }

    /**
     * Determines the type of PsiStatement based on its class and structure.
     *
     * @param statement The PsiStatement to be identified.
     * @return A String representing the type of the PsiStatement.
     */
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
        // Return "Not identified" if the statement type is not recognized
        return "Not identified";
    }

    /**
     * Processes a PsiForStatement, analyzing its body and recursively identifying control flow statements.
     *
     * @param forStatement        The PsiForStatement to be analyzed.
     * @param method              The containing method of the PsiForStatement.
     * @param stringBuilder       A StringBuilder to build the call sequence.
     * @param processedStatements A Set to keep track of processed statements.
     * @param basePath            The base path of the project.
     * @param methodCalls         A list that preserves the identified sequences.
     */
    private void processForStatement(PsiForStatement forStatement, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Get the body of the for statement
        PsiStatement thenBranch = forStatement.getBody();
        assert thenBranch != null;

        // Create a StringBuilder for the child call sequence, indicating the start of a "For" loop
        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> For ");

        // Iterate through statements in the body of the for statement and identify control flow statements
        for (PsiStatement innerStatement : PsiTreeUtil.findChildrenOfType(thenBranch, PsiStatement.class)) {
            // Recursively analyze control flow statements within the body of the for statement
            controlFlowStatementsIdentifier(innerStatement, basePath, method, childStringBuilder, processedStatements, methodCalls);
        }
    }

    /**
     * Processes a PsiIfStatement, analyzing its then branch, including both "If-else" and "Else" branches,
     * and recursively identifies control flow statements.
     *
     * @param ifStatement           The PsiIfStatement to be analyzed.
     * @param method                The containing method of the PsiIfStatement.
     * @param stringBuilder         A StringBuilder to build the call sequence.
     * @param processedStatements   A Set to keep track of processed statements.
     * @param basePath              The base path of the project.
     * @param methodCalls           A list that preserves the identified sequences.
     */
    private void processIfStatement(PsiIfStatement ifStatement, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Get the then branch of the if statement
        PsiStatement thenBranch = ifStatement.getThenBranch();
        assert thenBranch != null;

        // Create a StringBuilder for the child call sequence, indicating the start of an "If" branch
        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> If ");

        // Iterate through statements in the then branch and identify control flow statements
        for (PsiStatement innerStatement : PsiTreeUtil.findChildrenOfType(thenBranch, PsiStatement.class)) {
            // Recursively analyze control flow statements within the then branch
            controlFlowStatementsIdentifier(innerStatement, basePath, method, childStringBuilder, processedStatements, methodCalls);
        }

        // Check for the existence of the "else" branch
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != null) {
            // Create a StringBuilder for the child call sequence, indicating the start of an "Else" branch
            childStringBuilder = new StringBuilder(stringBuilder + " --> If-Else ");

            // Iterate through statements in the else branch and identify control flow statements
            for (PsiStatement innerStatement : PsiTreeUtil.findChildrenOfType(elseBranch, PsiStatement.class)) {
                // Recursively analyze control flow statements within the else branch
                controlFlowStatementsIdentifier(innerStatement, basePath, method, childStringBuilder, processedStatements, methodCalls);
            }
        }
    }

    /**
     * Processes a PsiWhileStatement, analyzing its body and identifying control flow statements.
     *
     * @param whileStatement       The PsiWhileStatement to be analyzed.
     * @param method               The containing method of the while statement.
     * @param stringBuilder        A StringBuilder to build the call sequence.
     * @param processedStatements  A Set to keep track of processed statements.
     * @param basePath             The base path of the project.
     * @param methodCalls          A list that preserves the identified sequences.
     */
    private void processWhileStatement(PsiWhileStatement whileStatement, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Get the body of the while statement
        PsiStatement thenBranch = whileStatement.getBody();
        assert thenBranch != null;

        // Create a StringBuilder for the child call sequence, indicating the start of a "While" block
        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> While ");

        // Iterate through statements in the while block and identify control flow statements
        for (PsiStatement innerStatement : PsiTreeUtil.findChildrenOfType(thenBranch, PsiStatement.class)) {
            // Recursively analyze control flow statements within the while block
            controlFlowStatementsIdentifier(innerStatement, basePath, method, childStringBuilder, processedStatements, methodCalls);
        }
    }

    /**
     * Processes a PsiDoWhileStatement, analyzing the statements within its body and identifying control flow statements.
     *
     * @param doWhileStatement      The PsiDoWhileStatement to be analyzed.
     * @param method                The PsiMethod containing the do-while statement.
     * @param stringBuilder         A StringBuilder to build the call sequence.
     * @param processedStatements   A Set to keep track of processed statements.
     * @param basePath              The base path of the project.
     * @param methodCalls           A list that preserves the identified sequences.
     */
    private void processDoWhileStatement(PsiDoWhileStatement doWhileStatement, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Get the body of the do-while statement
        PsiStatement thenBranch = doWhileStatement.getBody();
        assert thenBranch != null;

        // Build a call sequence indicating the start of a do-while loop
        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> Do-While ");

        // Analyze statements within the do-while body
        for (PsiStatement doWhileStatementInner : PsiTreeUtil.findChildrenOfType(thenBranch, PsiStatement.class)) {
            // Recursively analyze control flow statements within the do-while body
            controlFlowStatementsIdentifier(doWhileStatementInner, basePath, method, childStringBuilder, processedStatements, methodCalls);
        }
    }

    /**
     * Processes a PsiSwitchStatement, analyzing its body and identifying control flow statements.
     *
     * @param switchStatement      The PsiSwitchStatement to be analyzed.
     * @param method               The containing method of the switch statement.
     * @param stringBuilder        A StringBuilder to build the call sequence.
     * @param processedStatements  A Set to keep track of processed statements.
     * @param basePath             The base path of the project.
     * @param methodCalls          A list that preserves the identified sequences.
     */
    private void processSwitchStatement(PsiSwitchStatement switchStatement, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Get the body of the switch statement
        PsiCodeBlock switchBlock = switchStatement.getBody();
        assert switchBlock != null;

        // Create a StringBuilder for the child call sequence, indicating the start of a "Switch" block
        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> Switch ");

        // Iterate through statements in the switch block and identify control flow statements
        for (PsiStatement switchStatementInner : PsiTreeUtil.findChildrenOfType(switchBlock, PsiStatement.class)) {
            // Recursively analyze control flow statements within the switch block
            controlFlowStatementsIdentifier(switchStatementInner, basePath, method, childStringBuilder, processedStatements, methodCalls);
        }
    }

    /**
     * Processes a PsiTryStatement, analyzing its try block, catch blocks, and finally block.
     *
     * @param tryStatement         The PsiTryStatement to be analyzed.
     * @param method               The containing method of the PsiTryStatement.
     * @param stringBuilder        A StringBuilder to build the call sequence.
     * @param processedStatements  A Set to keep track of processed statements.
     * @param basePath             The base path of the project.
     * @param methodCalls          A list that preserves the identified sequences.
     */
    private void processTryStatement(PsiTryStatement tryStatement, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Get the try block of the try statement
        PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        assert tryBlock != null;

        // Create a StringBuilder for the child call sequence, indicating the start of a "Try" block
        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> Try ");

        // Iterate through statements in the try block and identify control flow statements
        for (PsiStatement tryStatementInner : PsiTreeUtil.findChildrenOfType(tryBlock, PsiStatement.class)) {
            controlFlowStatementsIdentifier(tryStatementInner, basePath, method, childStringBuilder, processedStatements, methodCalls);
        }

        // Get catch blocks from the try statement
        PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for (PsiCodeBlock catchBlock : catchBlocks) {
            processCatchBlock(catchBlock, method, stringBuilder, processedStatements, basePath, methodCalls);
        }

        // Get the finally block from the try statement
        PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
            processFinallyBlock(finallyBlock, method, stringBuilder, processedStatements, basePath, methodCalls);
        }
    }

    /**
     * Processes a PsiCodeBlock representing a catch block, analyzing its statements.
     *
     * @param catchBlock           The PsiCodeBlock representing the catch block to be analyzed.
     * @param method               The containing method of the catch block.
     * @param stringBuilder        A StringBuilder to build the call sequence.
     * @param processedStatements  A Set to keep track of processed statements.
     * @param basePath             The base path of the project.
     * @param methodCalls          A list that preserves the identified sequences.
     */
    private void processCatchBlock(PsiCodeBlock catchBlock, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Create a StringBuilder for the child call sequence, indicating the start of a "Catch" block
        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> Catch ");

        // Iterate through statements in the catch block and identify control flow statements
        for (PsiStatement catchStatement : PsiTreeUtil.findChildrenOfType(catchBlock, PsiStatement.class)) {
            // Recursively analyze control flow statements within the catch block
            controlFlowStatementsIdentifier(catchStatement, basePath, method, childStringBuilder, processedStatements, methodCalls);
        }
    }

    /**
     * Processes a PsiCodeBlock representing a finally block, analyzing its statements.
     *
     * @param finallyBlock         The PsiCodeBlock representing the finally block to be analyzed.
     * @param method               The containing method of the finally block.
     * @param stringBuilder        A StringBuilder to build the call sequence.
     * @param processedStatements  A Set to keep track of processed statements.
     * @param basePath             The base path of the project.
     * @param methodCalls          A list that preserves the identified sequences.
     */
    private void processFinallyBlock(PsiCodeBlock finallyBlock, PsiMethod method, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Create a StringBuilder for the child call sequence, indicating the start of a "Finally" block
        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> Finally ");

        // Iterate through statements in the finally block and identify control flow statements
        for (PsiStatement finallyStatement : PsiTreeUtil.findChildrenOfType(finallyBlock, PsiStatement.class)) {
            // Recursively analyze control flow statements within the finally block
            controlFlowStatementsIdentifier(finallyStatement, basePath, method, childStringBuilder, processedStatements, methodCalls);
        }
    }

    /**
     * Processes a PsiExpressionStatement representing a method call, analyzing the called method's body
     * and identifying control flow statements within it.
     *
     * @param methodCallStatement  The PsiExpressionStatement representing the method call to be analyzed.
     * @param stringBuilder        A StringBuilder to build the call sequence.
     * @param processedStatements  A Set to keep track of processed statements.
     * @param basePath             The base path of the project.
     * @param methodCalls          A list that preserves the identified sequences.
     */
    private void processMethodCallStatement(PsiExpressionStatement methodCallStatement, StringBuilder stringBuilder, Set<PsiStatement> processedStatements, String basePath, List<String> methodCalls) {
        // Resolve the method called in the expression
        PsiMethod resolvedMethod = ((PsiMethodCallExpression) methodCallStatement.getExpression()).resolveMethod();

        if (resolvedMethod != null) {
            // Check if the method is within the project's base path
            if (resolvedMethod.getContainingFile().getVirtualFile().getPath().startsWith(basePath)) {
                // Analyze the method's body for control flow statements
                PsiCodeBlock body = resolvedMethod.getBody();
                if (body != null) {
                    for (PsiStatement statement : body.getStatements()) {
                        // Build a child call sequence for the method's body
                        StringBuilder childStringBuilder = new StringBuilder(stringBuilder.toString() + " --> " + resolvedMethod.getName());

                        // Recursively analyze control flow statements within the method's body
                        controlFlowStatementsIdentifier(statement, basePath, resolvedMethod, childStringBuilder, processedStatements, methodCalls);
                    }
                }
            } else {
                // The method is outside the project's base path, add the method call to the list
                methodCalls.add(stringBuilder.toString() + " --> " + resolvedMethod.getName());
            }
        }
    }

    /**
     * Processes a PsiDeclarationStatement to determine if it contains a method call expression.
     *
     * @param statement      The PsiDeclarationStatement to be analyzed.
     * @param stringBuilder  A StringBuilder to build the call sequence.
     * @param methodCalls           A list that preserves the identified sequences.
     */
    private void processDeclarationStatement(PsiDeclarationStatement statement, StringBuilder stringBuilder, List<String> methodCalls) {
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

                    // Add the method name to the list
                    methodCalls.add(stringBuilder.toString() + " --> " + methodReference.getReferenceName());
                }
            }
        }
    }

    /**
     * Displays a dialog with the identified overwritten callback functions and their call sequences.
     *
     * @param project             The project context.
     * @param methodCalls         List of overwritten callback functions and their call sequences.
     */
    private void showDialog(Project project, List<String> methodCalls) {
        String title = "All the Possible Call Sequences";

        StringBuilder message = new StringBuilder("Number of sequences: " + methodCalls.size() + "\n\n");

        for (String callback : methodCalls) {
            message.append(callback).append("\n").append("---------------------").append("\n");
        }

        // Display the dialog with the results
        Messages.showMessageDialog(project, message.toString(), title, Messages.getInformationIcon());
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        e.getPresentation().setEnabled(editor != null && psiFile != null);
    }
}