package br.edu.ufape.taiti.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Classe responsável por criar a interface gráfica na configuração do plugin, disponível em File > Settings > TAITI.
 * Adaptada para configurações do Trello.
 */
public class TaitiSettingsComponent {

    private final JPanel mainPanel;
    // Campos para Trello
    private final JBTextField trelloBoardUrlOrIdText;
    private final JBTextField trelloApiKeyText;
    private final JBPasswordField trelloServerToken;

    // Campos existentes
    private final JBTextField githubURLText;
    private final JBTextField scenariosFolder;
    private final JBTextField stepDefinitionsFolder;
    private final JBTextField unityTestFolder;
    private final JBCheckBox structuralDependenciesCheckBox;
    private final JBCheckBox logicalDependenciesCheckBox;

    // Painel para o campo de token do Trello e botão de teste
    private final JPanel trelloServerTokenPanel = new JPanel(new BorderLayout(5, 0));

    public TaitiSettingsComponent() {
        // Inicialização dos campos do Trello
        trelloBoardUrlOrIdText = new JBTextField();
        trelloApiKeyText = new JBTextField();
        trelloServerToken = new JBPasswordField();

        // Inicialização dos campos existentes
        githubURLText = new JBTextField();
        scenariosFolder = new JBTextField("features");
        stepDefinitionsFolder = new JBTextField("features/step_definitions");
        unityTestFolder = new JBTextField("spec");
        structuralDependenciesCheckBox = new JBCheckBox("Including structural dependencies between files");
        logicalDependenciesCheckBox = new JBCheckBox("Including logical dependencies between files");

        // Configurar o campo de token do Trello dentro do trelloServerTokenPanel
        trelloServerTokenPanel.add(trelloServerToken, BorderLayout.CENTER);

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JLabel("<html><b>Project settings</b></html>"), new JSeparator(), 0)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("GitHub URL: "), githubURLText, 1, false)
                .addLabeledComponent(new JBLabel("Trello Board URL/ID: "), trelloBoardUrlOrIdText, 1, false)
                .addLabeledComponent(new JBLabel("Trello API Key: "), trelloApiKeyText, 1, false)
                .addLabeledComponent(new JBLabel("Trello Server Token: "), trelloServerTokenPanel, 1, false)
                .addVerticalGap(10)
                .addLabeledComponent(new JLabel("<html><b>Test settings</b></html>"), new JSeparator(), 0)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("Scenarios folder: "), scenariosFolder, 1, false)
                .addLabeledComponent(new JBLabel("Step definitions folder: "), stepDefinitionsFolder, 1, false)
                .addLabeledComponent(new JBLabel("Unity tests folder: "), unityTestFolder, 1, false)
                .addVerticalGap(10)
                .addLabeledComponent(new JLabel("<html><b>Code analysis settings</b></html>"), new JSeparator(), 0)
                .addVerticalGap(10)
                .addComponent(structuralDependenciesCheckBox)
                .addComponent(logicalDependenciesCheckBox)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    /**
     * Adiciona o botão de teste ao lado do campo de token do Trello.
     *
     * @param button O botão de teste de conexão.
     */
    public void addTestConnectionButton(JButton button) {
        trelloServerTokenPanel.add(button, BorderLayout.EAST);
    }

    public JComponent getPreferredFocusedComponent() {
        return trelloBoardUrlOrIdText; // Foco inicial no campo de URL/ID do quadro Trello
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    // Getters e Setters para Trello
    @NotNull
    public String getTrelloBoardUrlOrIdText() {
        return trelloBoardUrlOrIdText.getText();
    }

    public void setTrelloBoardUrlOrIdText(@NotNull String text) {
        trelloBoardUrlOrIdText.setText(text);
    }

    @NotNull
    public String getTrelloApiKeyText() {
        return trelloApiKeyText.getText();
    }

    public void setTrelloApiKeyText(@NotNull String text) {
        trelloApiKeyText.setText(text);
    }

    @NotNull
    public String getTrelloServerToken() {
        return String.valueOf(trelloServerToken.getPassword());
    }

    public void setTrelloServerToken(@NotNull String text) {
        trelloServerToken.setText(text);
    }

    // Getters e Setters para campos existentes (permanecem os mesmos)
    @NotNull
    public String getGithubURLText() {
        return githubURLText.getText();
    }

    public void setGithubURLText(@NotNull String text) {
        githubURLText.setText(text);
    }

    @NotNull
    public String getScenariosFolder() {
        return scenariosFolder.getText();
    }

    public void setScenariosFolder(@NotNull String text) {
        scenariosFolder.setText(text);
    }

    @NotNull
    public String getStepDefinitionsFolder() {
        return stepDefinitionsFolder.getText();
    }

    public void setStepDefinitionsFolder(@NotNull String text) {
        stepDefinitionsFolder.setText(text);
    }

    @NotNull
    public String getUnityTestFolder() {
        return unityTestFolder.getText();
    }

    public void setUnityTestFolder(@NotNull String text) {
        unityTestFolder.setText(text);
    }

    public boolean isStructuralDependenciesEnabled() {
        return structuralDependenciesCheckBox.isSelected();
    }

    public void setStructuralDependenciesEnabled(boolean enabled) {
        structuralDependenciesCheckBox.setSelected(enabled);
    }

    public boolean isLogicalDependenciesEnabled() {
        return logicalDependenciesCheckBox.isSelected();
    }

    public void setLogicalDependenciesEnabled(boolean enabled) {
        logicalDependenciesCheckBox.setSelected(enabled);
    }
}