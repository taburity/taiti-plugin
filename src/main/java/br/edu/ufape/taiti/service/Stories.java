package br.edu.ufape.taiti.service;

import br.edu.ufape.taiti.service.TrelloService.TrelloApiException;
import br.ufpe.cin.tan.analysis.task.TodoTask;
import br.ufpe.cin.tan.conflict.PlannedTask;
import br.ufpe.cin.tan.exception.CloningRepositoryException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class Stories {
    private final List<Task> myUnstartedTasks;
    private final List<Task> otherPendingTasks;
    private final List<Task> noScenarioTasks;
    private final TrelloService trelloService;
    private String authenticatedTrelloUserId;

    private final Project project;
    private final String githubURL;

    // Categorias de listas do Trello
    private static final List<String> UNSTARTED_LIST_NAMES = Arrays.asList("TODO", "Backlog", "Unstarted");
    private static final List<String> PENDING_LIST_NAMES = Arrays.asList("DOING", "Doing", "In Progress");

    public Stories(TrelloService trelloService, Project project, String githubURL) {
        this.githubURL = githubURL;
        this.project = project;
        this.trelloService = trelloService;
        myUnstartedTasks = new ArrayList<>();
        otherPendingTasks = new ArrayList<>();
        noScenarioTasks = new ArrayList<>();

        try {
            this.authenticatedTrelloUserId = trelloService.getAuthenticatedUserId();
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to get authenticated Trello user ID: " + e.getMessage());
            this.authenticatedTrelloUserId = null;
        }
    }

    public void clearLists() {
        synchronized (myUnstartedTasks) { myUnstartedTasks.clear(); }
        synchronized (otherPendingTasks) { otherPendingTasks.clear(); }
        synchronized (noScenarioTasks) { noScenarioTasks.clear(); }
    }

    public void startList(ProgressIndicator indicator) {
        clearLists();

        if (this.authenticatedTrelloUserId == null) {
            indicator.setText("Error: Trello user authentication issue.");
            return;
        }

        try {
            JSONArray cardsOnBoard = trelloService.getCardsOnBoard();

            for (int i = 0; i < cardsOnBoard.length(); i++) {
                if (indicator.isCanceled()) break;

                JSONObject cardJson = cardsOnBoard.getJSONObject(i);
                String cardName = cardJson.optString("name", "N/A");

                indicator.setFraction((double) i / cardsOnBoard.length());
                indicator.setText("Processing Trello card " + (i + 1) + "/" + cardsOnBoard.length());
                indicator.setText2("Card: " + cardName);
                System.out.println("===================================================");
                System.out.println("CARD JSON: " + cardJson.toString());
                System.out.println("===================================================");
                Task trelloCardTask = new Task(cardJson, trelloService, project);
                String listId = trelloCardTask.getListId();
                String listName = "";

                if (listId != null && !listId.isEmpty()) {
                    try {
                        JSONObject listDetails = trelloService.getListDetails(listId);
                        listName = listDetails.optString("name", "");
                    } catch (IOException | InterruptedException e) {
                        System.err.println("Could not fetch list details: " + e.getMessage());
                    }
                }

                List<String> memberIds = trelloCardTask.getMemberIds();
                boolean isAssignedToCurrentUser = memberIds != null && memberIds.contains(authenticatedTrelloUserId);

                // Normalização para comparação (case-insensitive)
                String finalListName = listName;
                boolean isUnstarted = UNSTARTED_LIST_NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(finalListName));
                boolean isPending = PENDING_LIST_NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(finalListName));

                if (isAssignedToCurrentUser && isUnstarted) {
                    if (trelloCardTask.hasScenarios()) {
                        processCardForConflictAnalysis(trelloCardTask);
                        synchronized (myUnstartedTasks) { myUnstartedTasks.add(trelloCardTask); }
                    } else {
                        synchronized (noScenarioTasks) { noScenarioTasks.add(trelloCardTask); }
                    }
                } else if (isPending) {
                    // Outras tarefas em progresso (mesmo que não sejam do usuário atual)
                    if (trelloCardTask.hasScenarios()) {
                        processCardForConflictAnalysis(trelloCardTask);
                        synchronized (otherPendingTasks) { otherPendingTasks.add(trelloCardTask); }
                    } else {
                        synchronized (noScenarioTasks) { noScenarioTasks.add(trelloCardTask); }
                    }
                } else {
                    System.out.println("  > Card ignorado: " + cardName + " na lista [" + listName + "]");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to process Trello cards", e);
        }
    }

    private void processCardForConflictAnalysis(Task trelloCardTask) throws CloningRepositoryException {
        if (trelloCardTask.hasScenarios()) {
            ArrayList<LinkedHashMap<String, Serializable>> tests = trelloCardTask.getScenarios();

            // CORREÇÃO: O ID do Trello é alfanumérico (ex: 69c831...).
            // Como TodoTask exige um int, usamos o hashCode da String para gerar um ID numérico único.
            String trelloId = trelloCardTask.getId();
            int numericId = (trelloId != null) ? trelloId.hashCode() : 0;

            // Criando o TodoTask com o ID numérico gerado pelo hashCode
            TodoTask todoTask = new TodoTask(githubURL, numericId, tests);
            PlannedTask plannedTask = todoTask.generateTaskForConflictAnalysis();
            trelloCardTask.setiTesk(plannedTask);
        }
    }

    public List<Task> getMyUnstartedTasks() {
        return myUnstartedTasks;
    }

    public List<Task> getOtherPendingTasks() {
        return otherPendingTasks;
    }

    public List<Task> getNoScenarioTasks() {
        return noScenarioTasks;
    }
}
