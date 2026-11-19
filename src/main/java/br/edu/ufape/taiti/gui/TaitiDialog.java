package br.edu.ufape.taiti.gui;

import br.edu.ufape.taiti.gui.configuretask.table.TestsTableModel;
import br.edu.ufape.taiti.gui.taskbar.TaskBarGUI;
import br.edu.ufape.taiti.service.TrelloService;
import br.edu.ufape.taiti.service.Task;
import br.edu.ufape.taiti.settings.TaitiSettingsState;
import br.edu.ufape.taiti.tool.ScenarioTestInformation;
import br.edu.ufape.taiti.tool.TaitiTool;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaitiDialog extends DialogWrapper {

    private final MainPanel mainPanel;
    private final Task selectedTask;
    private final JBTable table;
    private final TaskBarGUI taskBarGUI;
    private TrelloService trelloService;
    private TaitiTool taiti;
    private final Project project;
    private final TestsTableModel testTable;

    public TaitiDialog(Project project, TaskBarGUI taskBarGUI, Task selectedTask) {
        super(project, true);
        this.taskBarGUI = taskBarGUI;
        this.project = project;
        this.selectedTask = selectedTask;

        this.mainPanel = new MainPanel(project);
        this.table = mainPanel.getTable();
        this.testTable = mainPanel.getTableModel();

        // Keep OK button state in sync with scenario count
        mainPanel.setOnSelectionChanged(this::updateOkButton);

        // Also listen on the table model directly (belt-and-suspenders)
        this.table.getModel().addTableModelListener(e ->
                SwingUtilities.invokeLater(this::updateOkButton));

        prepareServices();

        setTitle("TAITIr - Vincular testes à tarefa: " + selectedTask.getName());
        setSize(1280, 860);

        init();

        loadInitialScenarios();

        // Force OK state after loading
        SwingUtilities.invokeLater(this::updateOkButton);
    }

    // ---------------------------------------------------------------
    // OK button enable/disable
    // ---------------------------------------------------------------

    /**
     * Enables the OK button only when at least one test scenario is selected.
     * Called whenever the table model changes.
     */
    private void updateOkButton() {
        JButton okButton = getButton(getOKAction());
        if (okButton != null) {
            boolean hasTests = mainPanel.getSelectedScenarioCount() > 0;
            okButton.setEnabled(hasTests);
            okButton.setCursor(Cursor.getPredefinedCursor(
                    hasTests ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
        JButton cancelButton = getButton(getCancelAction());
        if (cancelButton != null) {
            cancelButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
    }

    // ---------------------------------------------------------------
    // DialogWrapper overrides
    // ---------------------------------------------------------------

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (mainPanel.getSelectedScenarioCount() <= 0) {
            return new ValidationInfo("Selecione pelo menos um cenário de teste.", table);
        }
        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel.getRootPanel();
    }

    @Override
    protected void doOKAction() {
        if (mainPanel.getSelectedScenarioCount() <= 0) return;

        super.doOKAction();
        String cardID = String.valueOf(selectedTask.getId());

        try {
            File file = taiti.createScenariosFile(mainPanel.getScenarios());
            trelloService.saveTaitiScenarios(file, cardID);

            if (taskBarGUI.getLoading() != null) {
                taskBarGUI.changeJpanel(taskBarGUI.getLoading());
            }

            taskBarGUI.refresh();
            taiti.deleteScenariosFile();
            JOptionPane.showMessageDialog(getRootPane(), "Testes vinculados com sucesso!");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(getRootPane(), "Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private void prepareServices() {
        TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
        settings.retrieveStoredCredentials(project).thenRun(() -> {
            try {
                taiti = new TaitiTool(project);
                trelloService = new TrelloService(
                        settings.getTrelloApiKey(),
                        settings.getTrelloServerToken(),
                        settings.getTrelloBoardUrlOrId(),
                        project
                );
                ApplicationManager.getApplication().invokeLater(this::updateOkButton);
            } catch (Exception e) {
                System.err.println("[TaitiDialog] Erro ao inicializar serviços: " + e.getMessage());
            }
        });
    }

    private void loadInitialScenarios() {
        if (!selectedTask.hasScenarios()) return;

        List<ScenarioTestInformation> scenarios = new ArrayList<>();
        List<LinkedHashMap<String, Serializable>> taskScenarios = selectedTask.getScenarios();
        if (taskScenarios != null) {
            for (Map<String, Serializable> scenarioMap : taskScenarios) {
                String filePath = (String) scenarioMap.get("path");
                Object linesObj = scenarioMap.get("lines");
                if (filePath != null && linesObj instanceof List) {
                    for (Object line : (List<?>) linesObj) {
                        scenarios.add(new ScenarioTestInformation(filePath, (Integer) line));
                    }
                }
            }
        }
        if (!scenarios.isEmpty()) {
            mainPanel.loadExistingScenarios(scenarios);
        }
    }
}
