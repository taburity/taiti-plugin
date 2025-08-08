package br.edu.ufape.taiti.settings;

import br.edu.ufape.taiti.service.TrelloService; // Assume que você tem uma TrelloService adaptada
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
// import org.jsoup.internal.StringUtil; // Usar TextUtils ou similar para checar strings vazias

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Esta classe é um controller para as configurações do plugin e está definida no arquivo plugin.xml.
 * Adaptada para configurações do Trello.
 */
public class TaitiSettingsConfigurable implements Configurable {

    private TaitiSettingsComponent component;
    private final Project project;

    // Padrão para validar IDs do Trello (geralmente alfanuméricos)
    private static final Pattern TRELLO_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    // Padrão para validar URLs de quadros do Trello
    private static final Pattern TRELLO_BOARD_URL_PATTERN = Pattern.compile("^https?://trello\\.com/b/([a-zA-Z0-9]+)(?:/[^/]+)?$");


    public TaitiSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "TAITI"; // Corrigido para TAITI, se "TAITIr" foi um typo
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return component.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        component = new TaitiSettingsComponent();        // Carregar configurações existentes quando a interface é criada
        TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
        
        // Carregar credenciais do PasswordSafe de forma síncrona para a interface
        try {
            settings.loadStoredCredentials(project); // Método síncrono
        } catch (Exception e) {
            // Log do erro se necessário
        }
        
        // Preencher os campos com os valores carregados
        component.setTrelloBoardUrlOrIdText(settings.getTrelloBoardUrlOrId());
        component.setTrelloApiKeyText(settings.getTrelloApiKey());
        component.setTrelloServerToken(settings.getTrelloServerToken());
        component.setGithubURLText(settings.getGithubURL());
        component.setScenariosFolder(settings.getScenariosFolder());
        component.setStepDefinitionsFolder(settings.getStepDefinitionsFolder());
        component.setUnityTestFolder(settings.getUnityTestFolder());
        component.setStructuralDependenciesEnabled(settings.isStructuralDependenciesEnabled());
        component.setLogicalDependenciesEnabled(settings.isLogicalDependenciesEnabled());

        JButton testButton = new JButton("Test Connection");
        testButton.setToolTipText("Test Trello connection with current credentials and Board URL/ID");
        testButton.addActionListener(e -> {
            String boardUrlOrId = component.getTrelloBoardUrlOrIdText();
            String apiKey = component.getTrelloApiKeyText();
            String serverToken = component.getTrelloServerToken();

            if (isBlank(boardUrlOrId) || isBlank(apiKey) || isBlank(serverToken)) {
                JOptionPane.showMessageDialog(
                        component.getPanel(),
                        "Please fill in Trello Board URL/ID, API Key, and Server Token fields.",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // Validação básica do Board URL/ID
            if (!isValidTrelloBoardUrlOrId(boardUrlOrId)) {
                JOptionPane.showMessageDialog(
                        component.getPanel(),
                        "The Trello Board URL/ID is malformed.\nExample URL: https://trello.com/b/boardId/board-name\nOr provide just the Board ID (e.g., AbCdEfGh).",
                        "URL/ID Error",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // Supondo que TrelloService foi adaptada
            TrelloService trelloService = new TrelloService(
                    apiKey,
                    serverToken,
                    boardUrlOrId, // TrelloService deve saber como extrair o ID se for URL
                    project
            );

            try {
                // O método checkBoardStatus() deve fazer a chamada à API do Trello
                int status = trelloService.checkBoardStatus();
                if (status == 200) {
                    JOptionPane.showMessageDialog(
                            component.getPanel(),
                            "Connection successful!",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } else if (status == 401) { // Unauthorized - API Key ou Token inválidos
                    JOptionPane.showMessageDialog(
                            component.getPanel(),
                            "Invalid API Key or Server Token. Please check your credentials.",
                            "Authentication Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                } else if (status == 404) { // Not Found - Quadro não encontrado
                    JOptionPane.showMessageDialog(
                            component.getPanel(),
                            "Board not found. Please verify the Board URL/ID and if you have access to it.",
                            "Board Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                } else {
                    JOptionPane.showMessageDialog(
                            component.getPanel(),
                            "Connection failed. Status code: " + status,
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            } catch (TrelloService.TrelloApiException trelloEx) {
                JOptionPane.showMessageDialog(
                        component.getPanel(),
                        "Trello API Error: " + trelloEx.getMessage(),
                        "API Error",
                        JOptionPane.ERROR_MESSAGE
                );
            } catch (IOException | InterruptedException ex) {
                JOptionPane.showMessageDialog(
                        component.getPanel(),
                        "Connection failed: " + ex.getMessage(),
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE
                );
                Thread.currentThread().interrupt(); // Restaura o status de interrupção
            }
        });

        component.addTestConnectionButton(testButton);
        return component.getPanel();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isValidTrelloBoardUrlOrId(String input) {
        if (isBlank(input)) return false;
        return TRELLO_BOARD_URL_PATTERN.matcher(input).matches() || TRELLO_ID_PATTERN.matcher(input).matches();
    }


    @Override
    public boolean isModified() {
        TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
          // Carregar credenciais atuais para comparação
        try {
            settings.loadStoredCredentials(project); // Método síncrono
        } catch (Exception e) {
            // Se não conseguir carregar, considera como modificado
            return true;
        }

        boolean modified = !component.getTrelloBoardUrlOrIdText().equals(settings.getTrelloBoardUrlOrId());
        modified |= !component.getTrelloApiKeyText().equals(settings.getTrelloApiKey());
        modified |= !component.getTrelloServerToken().equals(settings.getTrelloServerToken());

        modified |= !component.getGithubURLText().equals(settings.getGithubURL());
        modified |= !component.getScenariosFolder().equals(settings.getScenariosFolder());
        modified |= !component.getUnityTestFolder().equals(settings.getUnityTestFolder());
        modified |= !component.getStepDefinitionsFolder().equals(settings.getStepDefinitionsFolder());
        modified |= component.isStructuralDependenciesEnabled() != settings.isStructuralDependenciesEnabled();
        modified |= component.isLogicalDependenciesEnabled() != settings.isLogicalDependenciesEnabled();
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
        validateSettings();

        // Aplicar todas as configurações
        settings.setTrelloBoardUrlOrId(component.getTrelloBoardUrlOrIdText());
        settings.setTrelloApiKey(component.getTrelloApiKeyText());
        settings.setTrelloServerToken(component.getTrelloServerToken());

        settings.setGithubURL(component.getGithubURLText());
        settings.setScenariosFolder(component.getScenariosFolder());
        settings.setUnityTestFolder(component.getUnityTestFolder());
        settings.setStepDefinitionsFolder(component.getStepDefinitionsFolder());
        settings.setStructuralDependenciesEnabled(component.isStructuralDependenciesEnabled());
        settings.setLogicalDependenciesEnabled(component.isLogicalDependenciesEnabled());

        // Salvar credenciais sensíveis no PasswordSafe
        settings.storeCredentials(project);
        
        // Forçar salvamento das configurações não-sensíveis
        ApplicationManager.getApplication().saveSettings();
    }

    @Override
    public void reset() {
        TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
          // Carregar credenciais do PasswordSafe
        try {
            settings.loadStoredCredentials(project); // Método síncrono
        } catch (Exception e) {
            Logger.getInstance(TaitiSettingsConfigurable.class).error("Failed to load credentials", e);
        }

        // Preencher os campos com os valores carregados
        component.setTrelloBoardUrlOrIdText(settings.getTrelloBoardUrlOrId());
        component.setTrelloApiKeyText(settings.getTrelloApiKey());
        component.setTrelloServerToken(settings.getTrelloServerToken());
        component.setGithubURLText(settings.getGithubURL());
        component.setStepDefinitionsFolder(settings.getStepDefinitionsFolder());
        component.setUnityTestFolder(settings.getUnityTestFolder());
        component.setScenariosFolder(settings.getScenariosFolder());
        component.setStructuralDependenciesEnabled(settings.isStructuralDependenciesEnabled());
        component.setLogicalDependenciesEnabled(settings.isLogicalDependenciesEnabled());
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }

    private void validateSettings() throws ConfigurationException {
        // check if the Trello fields are empty
        if (isBlank(component.getTrelloBoardUrlOrIdText())) {
            throw new ConfigurationException("The Trello Board URL/ID field is empty.", "Cannot Save Settings");
        }
        if (isBlank(component.getTrelloApiKeyText())) {
            throw new ConfigurationException("The Trello API Key field is empty.", "Cannot Save Settings");
        }
        if (isBlank(component.getTrelloServerToken())) {
            throw new ConfigurationException("The Trello Server Token field is empty.", "Cannot Save Settings");
        }

        // check if other fields are empty
        if (isBlank(component.getGithubURLText())) {
            throw new ConfigurationException("The GitHub URL field is empty.", "Cannot Save Settings");
        }
        if (isBlank(component.getScenariosFolder())) {
            throw new ConfigurationException("The Scenarios Folder path field is empty.", "Cannot Save Settings");
        }
        if (isBlank(component.getStepDefinitionsFolder())) {
            throw new ConfigurationException("The Step Definitions Folder path field is empty.", "Cannot Save Settings");
        }
        if (isBlank(component.getUnityTestFolder())) {
            throw new ConfigurationException("The Unity Test Folder path field is empty.", "Cannot Save Settings");
        }

        // check if the Trello Board URL/ID is valid
        if (!isValidTrelloBoardUrlOrId(component.getTrelloBoardUrlOrIdText())) {
            throw new ConfigurationException(
                    "The Trello Board URL/ID is malformed.\nExample URL: https://trello.com/b/boardId/board-name\nOr provide just the Board ID.",
                    "Cannot Save Settings");
        }

        // Basic validation for GitHub URL (optional, can be more specific)
        try {
            new URL(component.getGithubURLText()).toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new ConfigurationException(
                    "The GitHub URL is malformed.",
                    "Cannot Save Settings");
        }
    }
}