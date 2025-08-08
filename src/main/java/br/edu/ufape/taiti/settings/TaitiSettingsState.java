package br.edu.ufape.taiti.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Esta classe é responsável por armazenar os estados das configurações do plugin.
 * Adaptada para configurações do Trello.
 */
@State(
    name = "TaitiSettingsState",
    storages = @Storage("taiti-settings.xml")
)
public class TaitiSettingsState implements PersistentStateComponent<TaitiSettingsState> {
    // Trello settings - Salvando no XML
    protected String trelloBoardUrlOrId = "";
    protected String trelloApiKey = "";
    protected String trelloServerToken = "";

    // Existing settings
    protected String githubURL = "";
    protected String scenariosFolder = "features";
    protected String stepDefinitionsFolder = "features/step_definitions";
    protected String unityTestFolder = "spec";
    private boolean structuralDependenciesEnabled = false;
    private boolean logicalDependenciesEnabled = false;

    @Override
    public @Nullable TaitiSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull TaitiSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    // Getters and Setters for Trello
    public String getTrelloBoardUrlOrId() {
        return trelloBoardUrlOrId;
    }

    public void setTrelloBoardUrlOrId(String trelloBoardUrlOrId) {
        this.trelloBoardUrlOrId = trelloBoardUrlOrId;
    }

    // Getters and Setters para credenciais do Trello
    public String getTrelloApiKey() {
        return trelloApiKey != null ? trelloApiKey : "";
    }

    public void setTrelloApiKey(String trelloApiKey) {
        this.trelloApiKey = trelloApiKey;
    }

    public String getTrelloServerToken() {
        return trelloServerToken != null ? trelloServerToken : "";
    }

    public void setTrelloServerToken(String trelloServerToken) {
        this.trelloServerToken = trelloServerToken;
    }

    // Getters and Setters for existing fields
    public boolean isStructuralDependenciesEnabled() {
        return structuralDependenciesEnabled;
    }

    public void setStructuralDependenciesEnabled(boolean structuralDependenciesEnabled) {
        this.structuralDependenciesEnabled = structuralDependenciesEnabled;
    }

    public boolean isLogicalDependenciesEnabled() {
        return logicalDependenciesEnabled;
    }

    public void setLogicalDependenciesEnabled(boolean logicalDependenciesEnabled) {
        this.logicalDependenciesEnabled = logicalDependenciesEnabled;
    }

    public String getScenariosFolder() {
        return scenariosFolder;
    }

    public void setScenariosFolder(String scenariosFolder) {
        this.scenariosFolder = scenariosFolder;
    }

    public String getStepDefinitionsFolder() {
        return stepDefinitionsFolder;
    }

    public void setStepDefinitionsFolder(String stepDefinitionsFolder) {
        this.stepDefinitionsFolder = stepDefinitionsFolder;
    }

    public String getUnityTestFolder() {
        return unityTestFolder;
    }

    public void setUnityTestFolder(String unityTestFolder) {
        this.unityTestFolder = unityTestFolder;
    }

    public String getGithubURL() {
        return githubURL;
    }

    public void setGithubURL(String githubURL) {
        this.githubURL = githubURL;
    }

    public static TaitiSettingsState getInstance(Project project) {
        return project.getService(TaitiSettingsState.class);
    }

    public CompletableFuture<Void> retrieveStoredCredentials(Project project) {
        // Credenciais são carregadas automaticamente do XML
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Versão síncrona para carregar credenciais na interface de configuração
     */
    public void loadStoredCredentials(Project project) {
        // Credenciais são carregadas automaticamente do XML
    }

    public void storeCredentials(Project project) {
        // Credenciais são salvas automaticamente no XML via getState()
    }
}