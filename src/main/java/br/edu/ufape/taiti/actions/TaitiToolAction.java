package br.edu.ufape.taiti.actions;

import br.edu.ufape.taiti.gui.TaitiDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class TaitiToolAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getData(CommonDataKeys.PROJECT);

        TaitiDialog taitiDialog = new TaitiDialog(project);
        taitiDialog.setSize(1200,800);
        taitiDialog.setResizable(false);
        taitiDialog.show();
    }
}
