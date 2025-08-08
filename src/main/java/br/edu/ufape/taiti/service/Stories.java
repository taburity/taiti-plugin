package br.edu.ufape.taiti.service;

// import br.edu.ufape.taiti.exceptions.HttpException; // Replaced by TrelloApiException if applicable
import br.edu.ufape.taiti.service.TrelloService.TrelloApiException; // Assuming TrelloService has this
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
import java.util.Map;

public class Stories {
    private final List<Task> myUnstartedTasks; // Renamed from myUnstartedStories
    private final List<Task> otherPendingTasks;  // Renamed from otherPendingStories
    private final List<Task> noScenarioTasks;

    private final TrelloService trelloService; // Changed from PivotalTracker
    private String authenticatedTrelloUserId; // Changed from ownerID

    private final Project project; // Keep if TaitiTool or Task needs it
    private final String githubURL;  // Keep if TodoTask needs it

    // Define Trello list names that categorize tasks
    // These should match the names of your lists in Trello
    private static final List<String> UNSTARTED_LIST_NAMES = Arrays.asList("TODO", "Backlog", "Unstarted");
    private static final List<String> PENDING_LIST_NAMES = Arrays.asList("DOING");


    public Stories(TrelloService trelloService, Project project, String githubURL) {
        this.githubURL = githubURL;
        this.project = project;
        this.trelloService = trelloService;
        myUnstartedTasks = new ArrayList<>();
        otherPendingTasks = new ArrayList<>();
        noScenarioTasks = new ArrayList<>();

        try {
            // Fetch the authenticated user's Trello ID
            this.authenticatedTrelloUserId = trelloService.getAuthenticatedUserId();
        } catch (IOException | InterruptedException e) {
            // Handle cases where the user ID cannot be fetched (e.g., auth error)
            // This might involve logging the error, showing a notification, or re-throwing
            System.err.println("Failed to get authenticated Trello user ID: " + e.getMessage());
            this.authenticatedTrelloUserId = null; // Or throw a custom exception
            // Depending on the severity, you might want to prevent further operations.
            // For now, we'll allow it to proceed, but operations requiring the user ID might fail.
            // throw new RuntimeException("Failed to initialize Stories due to Trello auth issue", e);
        }
    }

    public void clearLists() {
        myUnstartedTasks.clear();
        otherPendingTasks.clear();
        noScenarioTasks.clear();
    }

    public void startList(ProgressIndicator indicator) {
        clearLists();

        if (this.authenticatedTrelloUserId == null) {
            // If user ID couldn't be fetched, we can't correctly categorize "my" tasks.
            // You might want to throw an error or show a notification here.
            System.err.println("Cannot load tasks: Authenticated Trello user ID is not available.");
            // Optionally, inform the user via indicator or a more prominent UI message.
            indicator.setText("Error: Trello user authentication issue.");
            return;
        }

        try {
            // Fetch all cards from the configured Trello board
            JSONArray cardsOnBoard = trelloService.getCardsOnBoard(); // Assumes this method exists in TrelloService

            for (int i = 0; i < cardsOnBoard.length(); i++) {
                if (indicator.isCanceled()) {
                    break;
                }
                JSONObject cardJson = cardsOnBoard.getJSONObject(i);
                indicator.setFraction((double) i / cardsOnBoard.length());
                indicator.setText("" +
                        "Processing Trello card " + (i + 1) + " of " + cardsOnBoard.length());
                indicator.setText2("Card Name: " + cardJson.optString("name", "N/A"));


                // The Task constructor needs to be adapted to take a Trello card JSONObject
                // and the TrelloService instance (if needed for further lookups within Task)
                Task trelloCardTask = new Task(cardJson, trelloService, project);

                String listId = trelloCardTask.getListId(); // Assumes Task can provide its list ID
                String listName = "";
                if (listId != null && !listId.isEmpty()) {
                    // Fetch list details to get the name
                    // This could be optimized by caching list names if TrelloService supports it
                    // or if Task constructor handles this lookup.
                    try {
                        JSONObject listDetails = trelloService.getListDetails(listId);
                        listName = listDetails.optString("name", "");
                    } catch (IOException | InterruptedException e) {
                        System.err.println("Could not fetch list details for list ID " + listId + ": " + e.getMessage());
                        // Continue processing other cards, or handle error more strictly
                    }
                }


                List<String> memberIds = trelloCardTask.getMemberIds(); // Assumes Task can provide assigned member IDs
                boolean isAssignedToCurrentUser = memberIds != null && memberIds.contains(authenticatedTrelloUserId);

                // Categorize based on list name and assignment
                if (isAssignedToCurrentUser && UNSTARTED_LIST_NAMES.contains(listName)) {
                    // Task is assigned to the current user and is in an "unstarted" list
                    if (trelloCardTask.hasScenarios()) {
                        processCardForConflictAnalysis(trelloCardTask);
                        synchronized (myUnstartedTasks) {
                            myUnstartedTasks.add(trelloCardTask);
                        }
                    } else {
                        synchronized (noScenarioTasks) {
                            noScenarioTasks.add(trelloCardTask);
                        }
                    }
                } else if (PENDING_LIST_NAMES.contains(listName)) {
                    // Task is in a "pending" list (could be assigned to others or unassigned,
                    // or assigned to current user but in a "pending" but not "my unstarted" list like "In Progress")
                    // We specifically add to otherPendingTasks if NOT assigned to current user OR if it's in a list
                    // that implies it's active/pending for someone else to see.
                    // The original logic was: (isStarted || isUnstarted) && plannedStoryOwnerID != ownerID
                    // For Trello, this translates to: in a PENDING_LIST_NAME and (not assigned to me OR assigned to me but in a list like "In Progress")
                    // For simplicity here, we'll add to otherPendingTasks if it's in a PENDING_LIST and not in myUnstartedTasks.
                    // A more refined logic might be needed based on exact workflow.
                    if (!isAssignedToCurrentUser || (isAssignedToCurrentUser && !UNSTARTED_LIST_NAMES.contains(listName))) {
                        if (trelloCardTask.hasScenarios()) {
                            processCardForConflictAnalysis(trelloCardTask);
                            synchronized (otherPendingTasks) {
                                otherPendingTasks.add(trelloCardTask);
                            }
                        } else {
                            // If it's pending for others but has no scenarios, it might still be relevant for `noScenarioTasks`
                            // or a different category. The original code added to noScenarioTasks.
                            synchronized (noScenarioTasks) {
                                noScenarioTasks.add(trelloCardTask);
                            }
                        }
                    }
                }
                // Cards in other lists (e.g., "Done") are ignored by this logic
            }
        } catch (InterruptedException | IOException | CloningRepositoryException e) {
            // Log or handle the exception appropriately
            // e.g., show an error message to the user through the UI
            // For now, re-throwing as a runtime exception to indicate a critical failure
            System.err.println("Error processing Trello cards: " + e.getMessage());
            e.printStackTrace(); // It's good to print stack trace for debugging
            // Consider creating a custom, checked exception if this needs to be handled more gracefully upstream.
            throw new RuntimeException("Failed to process Trello cards", e);
        }
    }

    // Renamed from processPlannedStory to reflect Trello context
    private void processCardForConflictAnalysis(Task trelloCardTask) throws CloningRepositoryException {
        // This method's internal logic for TodoTask and PlannedTask depends heavily on
        // how trelloCardTask.getScenarios() is implemented for Trello and
        // whether the githubURL is still the correct source for code analysis.
        if (trelloCardTask.hasScenarios()) {
            ArrayList<LinkedHashMap<String, Serializable>> tests = trelloCardTask.getScenarios();
            String cardId = String.valueOf(trelloCardTask.getId()); // Trello card ID

            // The TodoTask might need adaptation if its constructor or methods expect Pivotal-specific data.
            // Assuming githubURL is still relevant for fetching code to analyze against scenarios.
            TodoTask todoTask = new TodoTask(githubURL, Integer.parseInt(cardId), tests);
            PlannedTask plannedTask = todoTask.generateTaskForConflictAnalysis();
            trelloCardTask.setiTesk(plannedTask); // Assumes Task has setiTesk()
        }
    }

    public List<Task> getMyUnstartedTasks() { // Renamed
        return myUnstartedTasks;
    }

    public List<Task> getOtherPendingTasks() { // Renamed
        return otherPendingTasks;
    }

    public List<Task> getNoScenarioTasks() {
        return noScenarioTasks;
    }
}
