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
import java.util.stream.Collectors;

public class FindCallbackFunctions extends AnAction {

    // List to store method call sequences
    List<String> methodCalls = new ArrayList<>();

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {

        // Retrieve the editor and PSI file from the action event
        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);

        // Check if the editor and PSI file are available
        if (editor == null || psiFile == null) {
            return;
        }

        // Search for all classes in the project and analyze their methods
        AllClassesSearch.search(GlobalSearchScope.projectScope(Objects.requireNonNull(editor.getProject())), editor.getProject()).forEach(psiClass -> {

            // Iterate through methods of each class and analyze the call graph using "printTree" function
            Arrays.asList(psiClass.getMethods()).forEach(psiMethod -> printTree(psiMethod, editor.getProject().getBasePath(), null, 0));

            return true;
        });

        // Remove redundant from the list of all possible method call sequences
        List<String> uniqueList = methodCalls.stream().distinct().collect(Collectors.toList());

        // Display the results in a dialog
        showDialog(editor.getProject(), uniqueList);
    }

    /**
     * Recursively traverses the call graph of a method and records call sequences.
     *
     * @param psiMethod          The method to analyze.
     * @param basePath           The base path of the project.
     * @param parentStringBuilder The StringBuilder representing the parent method in the call sequence.
     * @param depth              The depth of the recursion.
     */
    private void printTree(PsiMethod psiMethod, String basePath, StringBuilder parentStringBuilder, int depth) {

        // Check if the method is within the project's base path and the recursion depth is within limits
        if (psiMethod.getContainingFile().getVirtualFile().getPath().startsWith(basePath) && depth < 50) {

            // Check if the method is annotated with @Override or is part of an overridden sequence
            PsiAnnotation overrideAnnotation = psiMethod.getModifierList().findAnnotation("java.lang.Override");
            if (overrideAnnotation != null || parentStringBuilder != null) {
                PsiCodeBlock body = psiMethod.getBody();
                if (body != null) {

                    // Retrieve statements from the method body
                    PsiStatement[] statements = body.getStatements();

                    // Iterate through statements to find method calls
                    for (PsiStatement statement : statements) {

                        // Find method call expressions in the statement
                        Collection<PsiMethodCallExpression> methodCallExpressions =
                                PsiTreeUtil.findChildrenOfType(statement, PsiMethodCallExpression.class);
                        for (PsiMethodCallExpression methodCallExpression : methodCallExpressions) {
                            String lastMethod;
                            if (parentStringBuilder == null)
                                lastMethod = psiMethod.getName();
                            else
                                lastMethod = parentStringBuilder.toString();
                            StringBuilder stringBuilder = new StringBuilder(lastMethod);

                            String methodName = methodCallExpression.getMethodExpression().getReferenceName();
                            if (methodName != null) {
                                stringBuilder.append(" --> ").append(methodName);
                            }
                            // Recursively analyze the called method
                            printTree(Objects.requireNonNull(methodCallExpression.resolveMethod()), basePath, stringBuilder, depth + 1);
                        }
                    }
                }
            }
        } else {
            // Record the method call sequence and print a debug message
            methodCalls.add(parentStringBuilder.toString());
            System.out.println("Method call within @Override method: " + parentStringBuilder);
        }
    }

    /**
     * Displays a dialog with the identified overwritten callback functions and their call sequences.
     *
     * @param project             The project context.
     * @param overwrittenCallbacks List of overwritten callback functions and their call sequences.
     */
    private void showDialog(Project project, List<String> overwrittenCallbacks) {
        String title = "All the Possible Call Sequences";

        StringBuilder message = new StringBuilder("Number of sequences: " + overwrittenCallbacks.size() + "\n\n");

        for (String callback : overwrittenCallbacks) {
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
