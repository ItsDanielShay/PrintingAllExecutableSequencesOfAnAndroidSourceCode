package com.example.customoverwrittenidentifier;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FindCallbackFunctions extends AnAction {


    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {

        // Retrieve the editor and PSI file from the action event
        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);

        // Check if both editor and PSI file are available
        if (editor == null || psiFile == null) {
            return;
        }

        // Create a list to store all overwritten callback functions
        List<String> overwrittenCallbacks = new ArrayList<>();

        // Perform a global search for all classes in the project
        AllClassesSearch.search(GlobalSearchScope.projectScope(editor.getProject()), editor.getProject()).forEach(psiClass -> {

            // Visit each class and its methods
            psiClass.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitMethod(PsiMethod method) {
                    super.visitMethod(method);

                    // Check if the method is annotated with @Override
                    if (method.getAnnotation("java.lang.Override") != null) {

                        // Check if the method is located in the "main" directory
                        if (method.getContainingFile().getVirtualFile().getPath().contains("/main/")) {

                            // Get the document of the containing file
                            Document document = PsiDocumentManager.getInstance(editor.getProject()).getDocument(method.getContainingFile());

                            // Get the offset and line number of the method
                            int offset = method.getTextOffset();
                            int lineNumber = document.getLineNumber(offset) + 1;

                            // Add the method information to the list
                            overwrittenCallbacks.add("Method name: " + method.getName()
                                    + " | Line: " + lineNumber + " | Path: "
                                    + method.getContainingFile().getVirtualFile().getPath());
                        }
                    }
                }
            });
            // Return true to continue iterating through classes
            return true;
        });

        // Collect unique overwritten callback functions
        List<String> uniqueList = overwrittenCallbacks.stream().distinct().collect(Collectors.toList());

        // Display the dialog box with the list of overwritten functions
        showOverwrittenFunctionsDialog(editor.getProject(), uniqueList);
    }

    private void showOverwrittenFunctionsDialog(Project project, List<String> overwrittenCallbacks) {
        String title = "Overwritten functions";

        // Construct the message with all the overwritten callback functions
        StringBuilder message = new StringBuilder("All the callback functions overwritten by the developers:\n\n");

        for (String callback : overwrittenCallbacks) {
            message.append(callback).append("\n").append("---------------------").append("\n");
        }

        // Show the message dialog with the list of overwritten functions
        Messages.showMessageDialog(project, message.toString(), title, Messages.getInformationIcon());
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        // Enable the action only if both editor and PSI file are available
        e.getPresentation().setEnabled(editor != null && psiFile != null);
    }
}