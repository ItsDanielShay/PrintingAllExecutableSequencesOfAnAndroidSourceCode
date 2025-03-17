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

import java.util.*;

/**
 * A "Version B" that handles Java control flow (if/else, loops, switch, try, break/continue)
 * plus detects increments/decrements and assignments with method calls on the right-hand side.
 * Uses a depth limit to avoid infinite expansions.
 */
public class buildCallControlFlowGraph extends AnAction {

    // Maximum expansion depth to avoid infinite loops or recursion
    private static final int MAX_DEPTH = 10;

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

        Project project = editor.getProject();
        if (project == null) {
            return;
        }

        // Perform a global search for all classes in the project
        AllClassesSearch.search(GlobalSearchScope.projectScope(project), project).forEach(psiClass -> {
            psiClass.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitMethod(PsiMethod method) {
                    super.visitMethod(method);

                    // Check if the method is annotated with @Override
                    if (method.getAnnotation("java.lang.Override") != null) {
                        exploreOverriddenMethod(method, project.getBasePath(), methodCalls);
                    }
                }
            });
            return true;
        });

        // Display the results in a dialog
        showDialog(project, methodCalls);
    }

    /**
     * Explore an @Override method, retrieving all possible paths (as strings).
     */
    private void exploreOverriddenMethod(PsiMethod psiMethod,
                                         String basePath,
                                         List<String> methodCalls) {

        // Check if the method is within the project's base path
        PsiFile containingFile = psiMethod.getContainingFile();
        if (containingFile == null
                || containingFile.getVirtualFile() == null
                || !containingFile.getVirtualFile().getPath().startsWith(basePath)) {
            return;
        }

        // If it's annotated with @Override, proceed
        if (psiMethod.getAnnotation("java.lang.Override") == null) {
            return;
        }

        PsiCodeBlock body = psiMethod.getBody();
        if (body == null) {
            return;
        }

        // For every top-level statement in the method's body, explore
        for (PsiStatement statement : body.getStatements()) {
            Deque<PsiMethod> callChain = new ArrayDeque<>();
            callChain.push(psiMethod);

            StringBuilder pathSoFar = new StringBuilder(psiMethod.getName());
            exploreStatement(statement,
                    basePath,
                    pathSoFar,
                    callChain,
                    1, // starting depth
                    false, // not initially in a loop
                    methodCalls);
        }
    }

    /**
     * Recursively analyze a given statement, building paths in string form.
     */
    private void exploreStatement(PsiStatement statement,
                                  String basePath,
                                  StringBuilder pathSoFar,
                                  Deque<PsiMethod> callChain,
                                  int depth,
                                  boolean inLoop,
                                  List<String> methodCalls) {

        // If we exceed the MAX_DEPTH, stop further expansions
        if (depth > MAX_DEPTH) {
            methodCalls.add(pathSoFar.toString() + " --> (depth limit reached)");
            return;
        }
        if (statement == null) {
            return;
        }

        String statementType = getStatementType(statement);

        switch (statementType) {
            case "If":
                processIfStatement((PsiIfStatement) statement,
                        basePath,
                        pathSoFar,
                        callChain,
                        depth,
                        inLoop,
                        methodCalls);
                break;
            case "For":
                processForStatement((PsiForStatement) statement,
                        basePath,
                        pathSoFar,
                        callChain,
                        depth,
                        methodCalls);
                break;
            case "While":
                processWhileStatement((PsiWhileStatement) statement,
                        basePath,
                        pathSoFar,
                        callChain,
                        depth,
                        methodCalls);
                break;
            case "Do While":
                processDoWhileStatement((PsiDoWhileStatement) statement,
                        basePath,
                        pathSoFar,
                        callChain,
                        depth,
                        methodCalls);
                break;
            case "Switch":
                processSwitchStatement((PsiSwitchStatement) statement,
                        basePath,
                        pathSoFar,
                        callChain,
                        depth,
                        methodCalls);
                break;
            case "Try":
                processTryStatement((PsiTryStatement) statement,
                        basePath,
                        pathSoFar,
                        callChain,
                        depth,
                        inLoop,
                        methodCalls);
                break;
            case "Break":
                pathSoFar.append(" --> Break");
                methodCalls.add(pathSoFar.toString());
                break;
            case "Continue":
                pathSoFar.append(" --> Continue");
                methodCalls.add(pathSoFar.toString());
                break;
            case "MethodCall":
                processMethodCall((PsiExpressionStatement) statement,
                        basePath,
                        pathSoFar,
                        callChain,
                        depth,
                        inLoop,
                        methodCalls);
                break;
            case "Declaration":
                processDeclarationStatement((PsiDeclarationStatement) statement,
                        pathSoFar,
                        methodCalls);
                break;
            case "Assignment":
                processAssignmentStatement((PsiExpressionStatement) statement,
                        basePath,
                        pathSoFar,
                        callChain,
                        depth,
                        inLoop,
                        methodCalls);
                break;
            case "IncrementDecrement":
                pathSoFar.append(" --> [Increment/Decrement: ")
                        .append(statement.getText())
                        .append("]");
                methodCalls.add(pathSoFar.toString());
                break;
            default:
                // Possibly a block statement or truly unrecognized
                if (statement instanceof PsiBlockStatement) {
                    PsiBlockStatement block = (PsiBlockStatement) statement;
                    for (PsiStatement inner : block.getCodeBlock().getStatements()) {
                        exploreStatement(inner, basePath, new StringBuilder(pathSoFar),
                                callChain, depth, inLoop, methodCalls);
                    }
                } else {
                    // Generic/unknown statement => just record it
                    methodCalls.add(pathSoFar.toString() + " --> [Unidentified Statement]");
                }
                break;
        }
    }

    /**
     * Identify the statement type, with extra checks for increments, assignments, etc.
     */
    private String getStatementType(PsiStatement statement) {
        if (statement instanceof PsiIfStatement) {
            return "If";
        }
        else if (statement instanceof PsiForStatement) {
            return "For";
        }
        else if (statement instanceof PsiWhileStatement) {
            return "While";
        }
        else if (statement instanceof PsiDoWhileStatement) {
            return "Do While";
        }
        else if (statement instanceof PsiSwitchStatement) {
            return "Switch";
        }
        else if (statement instanceof PsiTryStatement) {
            return "Try";
        }
        else if (statement instanceof PsiBreakStatement) {
            return "Break";
        }
        else if (statement instanceof PsiContinueStatement) {
            return "Continue";
        }
        else if (statement instanceof PsiExpressionStatement) {
            // Check expression details
            PsiExpression expr = ((PsiExpressionStatement) statement).getExpression();
            if (expr instanceof PsiMethodCallExpression) {
                return "MethodCall";
            }
            // new: check if it's an assignment
            else if (expr instanceof PsiAssignmentExpression) {
                return "Assignment";
            }
            // check if it's increment/decrement
            else if (expr instanceof PsiPostfixExpression ||
                    expr instanceof PsiPrefixExpression) {
                // e.g. count++, ++count, count--
                return "IncrementDecrement";
            }
        }
        else if (statement instanceof PsiDeclarationStatement) {
            return "Declaration";
        }
        return "Not identified";
    }

    /* ========== PROCESSING METHODS ========== */

    private void processIfStatement(PsiIfStatement ifStmt,
                                    String basePath,
                                    StringBuilder pathSoFar,
                                    Deque<PsiMethod> callChain,
                                    int depth,
                                    boolean inLoop,
                                    List<String> methodCalls) {
        PsiExpression condition = ifStmt.getCondition();
        String conditionText = (condition == null) ? "If (?)" : ("If (" + condition.getText() + ")");
        StringBuilder thenPath = new StringBuilder(pathSoFar).append(" --> ").append(conditionText);

        // Then branch
        PsiStatement thenBranch = ifStmt.getThenBranch();
        if (thenBranch != null) {
            exploreSubStatements(thenBranch, basePath, thenPath, callChain, depth, inLoop, methodCalls);
        }

        // Else branch
        PsiStatement elseBranch = ifStmt.getElseBranch();
        if (elseBranch != null) {
            if (elseBranch instanceof PsiIfStatement) {
                StringBuilder elseIfPath = new StringBuilder(pathSoFar).append(" --> ElseIf");
                processIfStatement((PsiIfStatement) elseBranch,
                        basePath,
                        elseIfPath,
                        callChain,
                        depth,
                        inLoop,
                        methodCalls);
            } else {
                StringBuilder elsePath = new StringBuilder(pathSoFar).append(" --> Else");
                exploreSubStatements(elseBranch, basePath, elsePath, callChain, depth, inLoop, methodCalls);
            }
        }
    }

    private void processForStatement(PsiForStatement forStmt,
                                     String basePath,
                                     StringBuilder pathSoFar,
                                     Deque<PsiMethod> callChain,
                                     int depth,
                                     List<String> methodCalls) {
        PsiExpression condition = forStmt.getCondition();
        String conditionText = (condition == null) ? "For (?)" : ("For (" + condition.getText() + ")");
        StringBuilder forPath = new StringBuilder(pathSoFar).append(" --> ").append(conditionText);

        PsiStatement body = forStmt.getBody();
        if (body != null) {
            exploreSubStatements(body, basePath, forPath, callChain, depth, true, methodCalls);
        }
        methodCalls.add(forPath.toString() + " --> (exit for)");
    }

    private void processWhileStatement(PsiWhileStatement whileStmt,
                                       String basePath,
                                       StringBuilder pathSoFar,
                                       Deque<PsiMethod> callChain,
                                       int depth,
                                       List<String> methodCalls) {
        PsiExpression condition = whileStmt.getCondition();
        String conditionText = (condition == null) ? "While (?)" : ("While (" + condition.getText() + ")");
        StringBuilder whilePath = new StringBuilder(pathSoFar).append(" --> ").append(conditionText);

        PsiStatement body = whileStmt.getBody();
        if (body != null) {
            exploreSubStatements(body, basePath, whilePath, callChain, depth, true, methodCalls);
        }
        methodCalls.add(whilePath.toString() + " --> (exit while)");
    }

    private void processDoWhileStatement(PsiDoWhileStatement doWhileStmt,
                                         String basePath,
                                         StringBuilder pathSoFar,
                                         Deque<PsiMethod> callChain,
                                         int depth,
                                         List<String> methodCalls) {
        StringBuilder doPath = new StringBuilder(pathSoFar).append(" --> Do");
        PsiStatement body = doWhileStmt.getBody();
        if (body != null) {
            exploreSubStatements(body, basePath, doPath, callChain, depth, true, methodCalls);
        }

        PsiExpression condition = doWhileStmt.getCondition();
        String condText = (condition == null) ? "(?)" : condition.getText();
        doPath.append(" --> While(").append(condText).append(")");
        methodCalls.add(doPath.toString() + " --> (exit do-while)");
    }

    private void processSwitchStatement(PsiSwitchStatement switchStmt,
                                        String basePath,
                                        StringBuilder pathSoFar,
                                        Deque<PsiMethod> callChain,
                                        int depth,
                                        List<String> methodCalls) {
        StringBuilder switchPath = new StringBuilder(pathSoFar).append(" --> Switch");
        PsiCodeBlock body = switchStmt.getBody();
        if (body == null) {
            methodCalls.add(switchPath + " --> (empty switch)");
            return;
        }

        for (PsiStatement st : body.getStatements()) {
            if (st instanceof PsiSwitchLabelStatement) {
                PsiSwitchLabelStatement labelStmt = (PsiSwitchLabelStatement) st;
                if (labelStmt.isDefaultCase()) {
                    switchPath.append(" --> [default]");
                } else {
                    PsiCaseLabelElementList labelList = labelStmt.getCaseLabelElementList();
                    if (labelList != null) {
                        StringBuilder labels = new StringBuilder();
                        for (PsiCaseLabelElement elem : labelList.getElements()) {
                            if (labels.length() > 0) labels.append("|");
                            labels.append(elem.getText());
                        }
                        switchPath.append(" --> [case: ").append(labels).append("]");
                    }
                }
            } else {
                exploreStatement(st, basePath, new StringBuilder(switchPath),
                        callChain, depth, false, methodCalls);
            }
        }
        methodCalls.add(switchPath.toString() + " --> (exit switch)");
    }

    private void processTryStatement(PsiTryStatement tryStmt,
                                     String basePath,
                                     StringBuilder pathSoFar,
                                     Deque<PsiMethod> callChain,
                                     int depth,
                                     boolean inLoop,
                                     List<String> methodCalls) {
        StringBuilder tryPath = new StringBuilder(pathSoFar).append(" --> TryBlock");
        PsiCodeBlock tryBlock = tryStmt.getTryBlock();
        if (tryBlock != null) {
            for (PsiStatement s : tryBlock.getStatements()) {
                exploreStatement(s, basePath, new StringBuilder(tryPath),
                        callChain, depth, inLoop, methodCalls);
            }
        }

        // Catch sections
        for (PsiCatchSection c : tryStmt.getCatchSections()) {
            StringBuilder catchPath = new StringBuilder(pathSoFar).append(" --> Catch(");
            PsiParameter param = c.getParameter();
            if (param != null) {
                catchPath.append(param.getType().getCanonicalText());
            }
            catchPath.append(")");

            PsiCodeBlock catchBlock = c.getCatchBlock();
            if (catchBlock != null) {
                for (PsiStatement s : catchBlock.getStatements()) {
                    exploreStatement(s, basePath, new StringBuilder(catchPath),
                            callChain, depth, inLoop, methodCalls);
                }
            }
        }

        // Finally block
        PsiCodeBlock finallyBlock = tryStmt.getFinallyBlock();
        if (finallyBlock != null) {
            StringBuilder finallyPath = new StringBuilder(pathSoFar).append(" --> Finally");
            for (PsiStatement s : finallyBlock.getStatements()) {
                exploreStatement(s, basePath, new StringBuilder(finallyPath),
                        callChain, depth, inLoop, methodCalls);
            }
        }
        methodCalls.add(pathSoFar.toString() + " --> (end try)");
    }

    /** Process a method call, handling recursion or multi-function cycles. */
    private void processMethodCall(PsiExpressionStatement exprStmt,
                                   String basePath,
                                   StringBuilder pathSoFar,
                                   Deque<PsiMethod> callChain,
                                   int depth,
                                   boolean inLoop,
                                   List<String> methodCalls) {
        PsiMethodCallExpression callExpr = (PsiMethodCallExpression) exprStmt.getExpression();
        PsiMethod resolved = callExpr.resolveMethod();
        if (resolved == null) {
            pathSoFar.append(" --> [UnresolvedCall]");
            methodCalls.add(pathSoFar.toString());
            return;
        }

        // If not in same project path, just record
        PsiFile containingFile = resolved.getContainingFile();
        if (containingFile == null
                || containingFile.getVirtualFile() == null
                || !containingFile.getVirtualFile().getPath().startsWith(basePath)) {
            pathSoFar.append(" --> ").append(resolved.getName()).append(" (external)");
            methodCalls.add(pathSoFar.toString());
            return;
        }

        // Check for recursion or multi-method cycle
        if (callChain.contains(resolved)) {
            pathSoFar.append(" --> ").append(resolved.getName()).append(" (loop/cycle!)");
            if (depth < MAX_DEPTH) {
                expandMethodBody(resolved, basePath, pathSoFar, callChain, depth, inLoop, methodCalls);
            } else {
                methodCalls.add(pathSoFar.toString() + " (stopped expansion)");
            }
        } else {
            pathSoFar.append(" --> ").append(resolved.getName());
            expandMethodBody(resolved, basePath, pathSoFar, callChain, depth, inLoop, methodCalls);
        }
    }

    /** Process an assignment statement. If RHS is a method call, we expand it similarly to a method call. */
    private void processAssignmentStatement(PsiExpressionStatement exprStmt,
                                            String basePath,
                                            StringBuilder pathSoFar,
                                            Deque<PsiMethod> callChain,
                                            int depth,
                                            boolean inLoop,
                                            List<String> methodCalls) {

        PsiExpression expr = exprStmt.getExpression();
        if (!(expr instanceof PsiAssignmentExpression)) {
            methodCalls.add(pathSoFar.toString() + " --> [Assignment: " + exprStmt.getText() + "]");
            return;
        }

        PsiAssignmentExpression assignExpr = (PsiAssignmentExpression) expr;
        PsiExpression rhs = assignExpr.getRExpression();

        // If the RHS is a method call, we can treat it similarly to a normal method call
        if (rhs instanceof PsiMethodCallExpression) {
            pathSoFar.append(" --> [Assignment with MethodCall: ")
                    .append(assignExpr.getLExpression().getText())
                    .append(" = ");

            PsiMethodCallExpression callExpr = (PsiMethodCallExpression) rhs;
            PsiMethod resolved = callExpr.resolveMethod();
            if (resolved == null) {
                pathSoFar.append("[UnresolvedCall]]");
                methodCalls.add(pathSoFar.toString());
                return;
            }

            pathSoFar.append(resolved.getName()).append("]");
            // Now expand the method if it's in the same project
            PsiFile containingFile = resolved.getContainingFile();
            if (containingFile == null
                    || containingFile.getVirtualFile() == null
                    || !containingFile.getVirtualFile().getPath().startsWith(basePath)) {
                // external method
                methodCalls.add(pathSoFar.toString() + " (external assignment)");
                return;
            }

            // Check recursion
            if (callChain.contains(resolved)) {
                pathSoFar.append("(loop/cycle!)");
                if (depth < MAX_DEPTH) {
                    expandMethodBody(resolved, basePath, pathSoFar, callChain, depth, inLoop, methodCalls);
                } else {
                    methodCalls.add(pathSoFar.toString() + " (stopped expansion)");
                }
            } else {
                expandMethodBody(resolved, basePath, pathSoFar, callChain, depth, inLoop, methodCalls);
            }
        } else {
            // Just a normal assignment with no method call on RHS
            pathSoFar.append(" --> [Assignment: ").append(exprStmt.getText()).append("]");
            methodCalls.add(pathSoFar.toString());
        }
    }

    /**
     * Expand the body of a called method, respecting depth limit.
     */
    private void expandMethodBody(PsiMethod method,
                                  String basePath,
                                  StringBuilder pathSoFar,
                                  Deque<PsiMethod> callChain,
                                  int depth,
                                  boolean inLoop,
                                  List<String> methodCalls) {
        if (depth >= MAX_DEPTH) {
            methodCalls.add(pathSoFar.toString() + " --> (depth limit reached)");
            return;
        }

        callChain.push(method);
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            methodCalls.add(pathSoFar.toString() + " --> (empty method)");
            callChain.pop();
            return;
        }

        PsiStatement[] statements = body.getStatements();
        if (statements.length == 0) {
            methodCalls.add(pathSoFar.toString() + " --> (empty method)");
        } else {
            for (PsiStatement st : statements) {
                exploreStatement(st,
                        basePath,
                        new StringBuilder(pathSoFar),
                        callChain,
                        depth + 1,
                        inLoop,
                        methodCalls);
            }
        }
        callChain.pop();
    }

    /** Handle local variable declarations. */
    private void processDeclarationStatement(PsiDeclarationStatement decl,
                                             StringBuilder pathSoFar,
                                             List<String> methodCalls) {
        for (PsiElement element : decl.getDeclaredElements()) {
            if (element instanceof PsiVariable) {
                PsiVariable var = (PsiVariable) element;
                PsiExpression initializer = var.getInitializer();
                if (initializer instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression callExpr = (PsiMethodCallExpression) initializer;
                    String name = callExpr.getMethodExpression().getReferenceName();
                    methodCalls.add(pathSoFar.toString() + " --> [VarInitCall: " + name + "]");
                } else {
                    // Just a normal declaration
                    methodCalls.add(pathSoFar.toString() + " --> [Declaration: " + decl.getText() + "]");
                }
            }
        }
    }

    /**
     * Explore sub-statements if it's a block, else just single statement.
     */
    private void exploreSubStatements(PsiStatement statement,
                                      String basePath,
                                      StringBuilder pathSoFar,
                                      Deque<PsiMethod> callChain,
                                      int depth,
                                      boolean inLoop,
                                      List<String> methodCalls) {
        if (statement instanceof PsiBlockStatement) {
            PsiBlockStatement block = (PsiBlockStatement) statement;
            for (PsiStatement st : block.getCodeBlock().getStatements()) {
                exploreStatement(st, basePath, new StringBuilder(pathSoFar),
                        callChain, depth, inLoop, methodCalls);
            }
        } else {
            exploreStatement(statement, basePath, pathSoFar,
                    callChain, depth, inLoop, methodCalls);
        }
    }

    /**
     * Show final results in a dialog.
     */
    private void showDialog(Project project, List<String> methodCalls) {
        String title = "All the Possible Call Sequences (Version A - Capped at depth of 10)";
        StringBuilder message = new StringBuilder("Number of sequences: ").append(methodCalls.size()).append("\n\n");
        for (String seq : methodCalls) {
            message.append(seq).append("\n---------------------\n");
        }
        Messages.showMessageDialog(project, message.toString(), title, Messages.getInformationIcon());
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabled(editor != null && psiFile != null);
    }
}
