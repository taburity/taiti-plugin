package br.edu.ufape.taiti.gui;

import br.edu.ufape.taiti.exceptions.HttpException; // Pode ser necessário manter ou adaptar para TrelloApiException
import br.edu.ufape.taiti.gui.taskbar.LoadingScreen;
import br.edu.ufape.taiti.gui.taskbar.TaskBarGUI;
import br.edu.ufape.taiti.service.TrelloService; // Alterado de PivotalTracker para TrelloService
import br.edu.ufape.taiti.service.Task; // A classe Task pode precisar de adaptação para Trello
import br.edu.ufape.taiti.settings.TaitiSettingsState;
import br.edu.ufape.taiti.tool.ScenarioTestInformation;
import br.edu.ufape.taiti.tool.TaitiTool;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
// import org.jsoup.internal.StringUtil; // Se StringUtil for usado, garantir importação ou substituição

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class TaitiDialog extends DialogWrapper {

    private final MainPanel mainPanel;
    private final Task selectedTask; // Este objeto 'Task' agora representa um Cartão do Trello
    private final JBTable table;
    private final TaskBarGUI taskBarGUI;

    private TrelloService trelloService; // Alterado de pivotalTracker para trelloService
    private TaitiTool taiti;

    private final Project project;
    private boolean executed = false; // Esta variável não parece ser usada, pode ser removida se não tiver propósito
    private boolean servicesReady = false;

    public TaitiDialog(Project project, TaskBarGUI taskBarGUI, Task selectedTask) {
        super(true);

        this.taskBarGUI = taskBarGUI;
        this.project = project;
        this.selectedTask = selectedTask; // selectedTask.getId() agora é o cardID do Trello

        this.mainPanel = new MainPanel(project);
        this.table = mainPanel.getTable();

        prepareServices();

        setTitle("TAITI - Add Tests to: " + selectedTask.getName()); // selectedTask.getName() é o nome do cartão
        setSize(1200, 810);
        init();

        // A lógica para carregar cenários existentes do 'selectedTask' permanece.
        // Assume-se que 'selectedTask.hasScenarios()' e 'selectedTask.getScenarios()'
        // foram adaptados para buscar/representar dados do Trello (ex: de anexos ou descrição).
        if (selectedTask.hasScenarios()) {
            List<ScenarioTestInformation> scenarios = new ArrayList<>();
            List<LinkedHashMap<String, Serializable>> taskScenarios = selectedTask.getScenarios();

            if (taskScenarios != null) {
                for (Map<String, Serializable> scenarioMap : taskScenarios) {
                    String filePath = (String) scenarioMap.get("path");
                    Object linesObject = scenarioMap.get("lines");

                    if (filePath != null && linesObject instanceof List) {
                        @SuppressWarnings("unchecked") // Seguro devido à verificação instanceof
                        List<Integer> lines = (List<Integer>) linesObject;
                        for (int line : lines) {
                            scenarios.add(new ScenarioTestInformation(filePath, line));
                        }
                    }
                }
            }
            if (!scenarios.isEmpty()) {
                mainPanel.loadExistingScenarios(scenarios);
            }
        }
    }

    public MainPanel getMainPanel() {
        return mainPanel;
    }

    private void prepareServices() {
        TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
        settings.retrieveStoredCredentials(project).thenRun(() -> {
            try {
                taiti = new TaitiTool(project);
                // Instancia TrelloService com as configurações do Trello
                trelloService = new TrelloService(
                        settings.getTrelloApiKey(),
                        settings.getTrelloServerToken(),
                        settings.getTrelloBoardUrlOrId(), // TrelloService deve extrair o ID se for URL
                        project
                );
                servicesReady = true;
                // Habilitar o botão OK se os serviços estiverem prontos e a validação inicial passar
                if (getOKAction() != null) {
                    getOKAction().setEnabled(doValidateAll().isEmpty());
                }

            } catch (Exception e) {
                servicesReady = false; // Garante que servicesReady seja falso em caso de erro
                if (getOKAction() != null) { // Desabilita o botão OK se houver erro
                    getOKAction().setEnabled(false);
                }
                JOptionPane.showMessageDialog(getRootPane(),
                        "Error initializing Trello services: " + e.getMessage() +
                                "\nPlease check TAITI settings (File > Settings > TAITI).",
                        "Service Initialization Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }).exceptionally(ex -> {
            servicesReady = false;
            if (getOKAction() != null) {
                getOKAction().setEnabled(false);
            }
            JOptionPane.showMessageDialog(getRootPane(),
                    "Failed to retrieve settings: " + ex.getMessage(),
                    "Settings Error",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        });
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel.getRootPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (!servicesReady) {
            return new ValidationInfo("Trello services are not ready. Please check settings or wait.", mainPanel.getRootPanel());
        }
        if (table.getRowCount() == 0) { // Alterado de 1 para 0, se a tabela não tiver header fixo
            return new ValidationInfo("Select at least one scenario.", table);
        }
        // Adicionar mais validações se necessário
        return null;
    }

    @Override
    protected Action @NotNull [] createActions() {
        Action okAction = getOKAction();
        // Inicialmente desabilitar o botão OK até que os serviços estejam prontos e a validação passe
        // A habilitação ocorrerá em prepareServices ou após a primeira validação bem-sucedida.
        okAction.setEnabled(false);
        return new Action[]{okAction, getCancelAction()};
    }

    @Override
    protected void doOKAction() {
        // Validação final antes de prosseguir, embora doValidate() já deva ter sido chamado.
        if (doValidateAll().stream().anyMatch(vi -> vi.component == mainPanel.getRootPanel() || vi.component == table)) {
            // Se houver erros de validação críticos, não prosseguir.
            // JOptionPane já deve ter sido mostrado por doValidate se implementado para tal,
            // ou podemos mostrar um aqui.
            return;
        }

        super.doOKAction(); // Fecha o diálogo se a validação acima (implícita pelo DialogWrapper) passar

        String cardID = String.valueOf(selectedTask.getId()); // ID do Cartão do Trello

        try {
            File file = taiti.createScenariosFile(mainPanel.getScenarios());

            // Usa trelloService para salvar os cenários
            // O método em TrelloService pode ser saveTaitiScenarios, postTaitiCommentAndUploadFile, etc.
            // Assumindo um método saveTaitiScenarios que encapsula a lógica de upload e comentário.
            trelloService.saveTaitiScenarios(file, cardID);

            LoadingScreen loading = taskBarGUI.getLoading();
            if (loading != null) { // Verifica se loading não é nulo
                taskBarGUI.changeJpanel(loading);
            }
            taskBarGUI.refresh();
            taiti.deleteScenariosFile();

            JOptionPane.showMessageDialog(getRootPane(),
                    "Scenarios successfully saved to Trello card ID: " + cardID,
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (TrelloService.TrelloApiException e) { // Captura a exceção específica do TrelloService
            JOptionPane.showMessageDialog(getRootPane(),
                    "Trello API communication error: " + e.getMessage() + " (Status: " + e.getStatusCode() + ")",
                    "Trello API Error",
                    JOptionPane.ERROR_MESSAGE);
            if (taiti != null) taiti.deleteScenariosFile();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(getRootPane(),
                    "Error creating or handling scenarios file: " + e.getMessage(),
                    "File Error",
                    JOptionPane.ERROR_MESSAGE);
            if (taiti != null) taiti.deleteScenariosFile(); // Tenta limpar mesmo em caso de erro
        }
        // Removido HttpException genérico se TrelloApiException for mais específico
        // catch (HttpException e) { ... }
        catch (Exception e) { // Captura genérica para outros erros inesperados
            JOptionPane.showMessageDialog(getRootPane(),
                    "An unexpected error occurred: " + e.getMessage(),
                    "Unexpected Error",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace(); // Logar o stack trace para depuração
            if (taiti != null) taiti.deleteScenariosFile();
        }
    }
}