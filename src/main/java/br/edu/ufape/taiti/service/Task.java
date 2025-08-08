package br.edu.ufape.taiti.service;

// import br.edu.ufape.taiti.exceptions.HttpException; // Replaced by TrelloApiException if applicable
import br.edu.ufape.taiti.service.TrelloService.TrelloApiException;
import br.edu.ufape.taiti.tool.ScenarioTestInformation;
import br.edu.ufape.taiti.tool.TaitiTool;
import br.ufpe.cin.tan.conflict.PlannedTask;
import com.intellij.openapi.project.Project;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Task {

    private final String name;
    private final String id; // Trello card ID (string)
    private final String url;
    private final String description; // Trello card description
    private final String idList;      // ID of the Trello list the card belongs to
    private final List<String> memberIds; // List of member IDs assigned to the card
    private final List<String> labelNames; // List of label names on the card

    private String primaryPersonName; // Name of the first member, or a relevant assigned person
    private ArrayList<LinkedHashMap<String, Serializable>> scenarios = new ArrayList<>();
    private PlannedTask iTesk; // For conflict analysis

    private ArrayList<Task> conflictTasks = new ArrayList<>();
    private ArrayList<LinkedHashMap<String, Serializable>> conflictScenarios = new ArrayList<>();
    private double conflictRate;
    private boolean hasScenarios;

    // Fields to be potentially mapped from labels or list names, or might be omitted
    // private String type;
    // private String priority;
    // private String state; // This is now represented by idList, actual name resolved by Stories class

    /**
     * Constructor for a Task based on a Trello card JSONObject.
     *
     * @param cardJson      JSONObject representing a Trello card.
     * @param trelloService Instance of TrelloService to fetch related data.
     * @param project       IntelliJ project instance.
     */
    public Task(JSONObject cardJson, TrelloService trelloService, Project project) throws TrelloApiException, IOException, InterruptedException {
        this.id = cardJson.getString("id");
        this.name = cardJson.getString("name");
        this.url = cardJson.getString("url");
        this.description = cardJson.optString("desc", "");
        this.idList = cardJson.getString("idList");

        this.memberIds = new ArrayList<>();
        JSONArray membersArray = cardJson.optJSONArray("idMembers");
        if (membersArray != null) {
            for (int i = 0; i < membersArray.length(); i++) {
                this.memberIds.add(membersArray.getString(i));
            }
        }

        this.labelNames = new ArrayList<>();
        JSONArray labelsArray = cardJson.optJSONArray("labels");
        if (labelsArray != null) {
            for (int i = 0; i < labelsArray.length(); i++) {
                JSONObject labelObj = labelsArray.getJSONObject(i);
                this.labelNames.add(labelObj.optString("name", ""));
            }
        }

        // Attempt to set a primary person name (e.g., first member)
        // A more sophisticated approach might be needed if specific ownership is key
        setPrimaryPersonName(trelloService);
        // Load scenarios associated with this Trello card
        loadScenariosFromTrelloCard(trelloService, project);
    }

    private void setPrimaryPersonName(TrelloService trelloService) throws TrelloApiException, IOException, InterruptedException {
        if (this.memberIds != null && !this.memberIds.isEmpty()) {
            // For simplicity, try to get the name of the first member.
            // In a real scenario, you might want to fetch all members' details
            // or have a specific way to identify the "owner".
            try {
                // Trello API doesn't have a direct "get member details by ID" in the free tier easily accessible
                // without iterating through board members. We'll fetch all board members and find the match.
                // This can be inefficient if called for every task. Consider caching board members.
                JSONArray boardMembers = trelloService.getBoardMembers();
                for (String memberId : this.memberIds) { // Iterate through assigned members
                    for (int i = 0; i < boardMembers.length(); i++) {
                        JSONObject member = boardMembers.getJSONObject(i);
                        if (member.getString("id").equals(memberId)) {
                            this.primaryPersonName = member.optString("fullName", member.optString("username", "Unknown Member"));
                            return; // Found the first assigned member's name
                        }
                    }
                }
                if (this.primaryPersonName == null) {
                    this.primaryPersonName = "Unassigned/Unknown";
                }

            } catch (JSONException e) {
                System.err.println("Error parsing member data for card " + this.id + ": " + e.getMessage());
                this.primaryPersonName = "Error fetching name";
            }
        } else {
            this.primaryPersonName = "Unassigned";
        }
    }

    private void loadScenariosFromTrelloCard(TrelloService trelloService, Project project) throws TrelloApiException, IOException, InterruptedException {
        // Check for the [TAITI] Scenarios comment
        JSONObject taitiComment = trelloService.getTaitiCommentActionOnCard(this.id);

        if (taitiComment != null) {
            // If comment exists, try to download the associated .csv file (TrelloService handles this logic)
            File scenarioFile = trelloService.downloadTaitiFileFromCard(this.id);
            if (scenarioFile != null && scenarioFile.exists()) {
                this.hasScenarios = true;
                TaitiTool taitiTool = new TaitiTool(project);
                List<String[]> fileContent = taitiTool.readTaitiFile(scenarioFile); // Parses the CSV

                for (String[] lineData : fileContent) {
                    if (lineData.length >= 2) {
                        String absolutePath = lineData[0];
                        String numbersStringRaw = lineData[1];
                        String[] numbersStringArray = numbersStringRaw.replaceAll("[\\[\\]]", "").split(",\\s*");

                        ArrayList<Integer> numbersInt = new ArrayList<>();
                        for (String s : numbersStringArray) {
                            if (!s.trim().isEmpty()) {
                                try {
                                    numbersInt.add(Integer.parseInt(s.trim()));
                                } catch (NumberFormatException e) {
                                    System.err.println("Warning: Could not parse line number '" + s + "' for file " + absolutePath + " in card " + this.id);
                                }
                            }
                        }
                        if (!numbersInt.isEmpty()) {
                            LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(2);
                            map.put("path", absolutePath);
                            map.put("lines", numbersInt);
                            this.scenarios.add(map);
                        }
                    }
                }
                // Clean up the downloaded temporary file
                if (!scenarioFile.delete()) {
                    System.err.println("Warning: Could not delete temporary scenario file: " + scenarioFile.getAbsolutePath());
                }
            } else {
                this.hasScenarios = false;
                System.out.println("TAITI comment found on card " + this.id + ", but no scenario file (.csv) was downloaded or found.");
            }
        } else {
            this.hasScenarios = false;
        }
    }

    public List<ScenarioTestInformation> toScenarioTestInformationList() {
        List<ScenarioTestInformation> scenarioInfoList = new ArrayList<>();
        for (LinkedHashMap<String, Serializable> scenarioMap : getScenarios()) {
            String filePath = (String) scenarioMap.get("path");
            Object linesObject = scenarioMap.get("lines");

            if (filePath != null && linesObject instanceof ArrayList) {
                @SuppressWarnings("unchecked")
                ArrayList<Integer> lines = (ArrayList<Integer>) linesObject;
                for (int line : lines) {
                    scenarioInfoList.add(new ScenarioTestInformation(filePath, line));
                }
            }
        }
        return scenarioInfoList;
    }

    public boolean hasScenarios() {
        return hasScenarios;
    }

    // The conflict checking logic might need review based on how 'Task' objects are compared
    // and how scenarios are structured, but the core iteration logic can remain.
    public void checkConflictRisk(List<Task> listTask) {
        conflictTasks.clear();
        conflictScenarios.clear(); // Clear previous conflict scenarios
        conflictRate = 0;
        int totalComparisons = 0; // To calculate rate more accurately if needed

        for (LinkedHashMap<String, Serializable> currentScenario : this.scenarios) {
            String currentAbsolutePath = (String) currentScenario.get("path");
            @SuppressWarnings("unchecked")
            ArrayList<Integer> currentLines = (ArrayList<Integer>) currentScenario.get("lines");
            if (currentLines == null) continue;

            for (Task otherTask : listTask) {
                if (otherTask == this || otherTask.getScenarios() == null) continue; // Skip self or tasks with no scenarios

                boolean taskAddedToConflict = false;
                for (LinkedHashMap<String, Serializable> otherScenario : otherTask.getScenarios()) {
                    String otherAbsolutePath = (String) otherScenario.get("path");
                    @SuppressWarnings("unchecked")
                    ArrayList<Integer> otherLines = (ArrayList<Integer>) otherScenario.get("lines");
                    if (otherLines == null) continue;

                    if (currentAbsolutePath.equals(otherAbsolutePath)) {
                        ArrayList<Integer> conflictingLineNumbers = new ArrayList<>();
                        for (int currentLineNum : currentLines) {
                            if (otherLines.contains(currentLineNum)) {
                                conflictingLineNumbers.add(currentLineNum);
                                // conflictRate++; // This simple increment might not be the best rate metric
                            }
                        }

                        if (!conflictingLineNumbers.isEmpty()) {
                            LinkedHashMap<String, Serializable> conflictDetail = new LinkedHashMap<>();
                            conflictDetail.put("path", currentAbsolutePath);
                            conflictDetail.put("lines", conflictingLineNumbers);
                            conflictDetail.put("conflicting_task_id", otherTask.getId());
                            conflictDetail.put("conflicting_task_name", otherTask.getName());
                            this.conflictScenarios.add(conflictDetail);

                            if (!taskAddedToConflict) {
                                this.conflictTasks.add(otherTask);
                                taskAddedToConflict = true;
                            }
                        }
                    }
                }
            }
        }
        // A more meaningful conflict rate could be: (number of tasks with conflicts / total other tasks with scenarios)
        // Or (number of conflicting scenario lines / total scenario lines in this task)
        // For now, let's use the number of tasks that have at least one conflicting scenario line with this task.
        if (!listTask.isEmpty()) {
            // Calculate rate as percentage of other tasks that conflict
            long otherTasksWithScenarios = listTask.stream().filter(t -> t != this && t.hasScenarios()).count();
            if (otherTasksWithScenarios > 0) {
                this.conflictRate = ((double) this.conflictTasks.size() / otherTasksWithScenarios) * 100.0;
            } else {
                this.conflictRate = 0.0; // No other tasks with scenarios to conflict with
            }
        } else {
            this.conflictRate = 0.0;
        }

    }

    public ArrayList<Task> getConflictTasks() {
        return conflictTasks;
    }

    public ArrayList<LinkedHashMap<String, Serializable>> getConflictScenarios() {
        return conflictScenarios;
    }

    public double getConflictRate() {
        return conflictRate;
    }

    public void setConflictRate(double conflictRate) {
        this.conflictRate = conflictRate;
    }

    public ArrayList<LinkedHashMap<String, Serializable>> getScenarios() {
        return scenarios;
    }

    public String getPersonName() {
        return primaryPersonName;
    }

    // This method is no longer directly applicable as Trello uses member lists.
    // Person name is set in constructor or a dedicated method.
    // public void setName(List<Person> members){ ... }


    public String getName() {
        return name;
    }

    public String getId() { // Trello Card ID
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public String getListId() {
        return idList;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public List<String> getLabelNames() {
        return labelNames;
    }

    // Methods like getType, getPriority, getState from Pivotal are not direct fields in Trello.
    // They might be inferred from labels or list names if needed.
    // public String getType() { return type; }
    // public String getPriority() { return priority; }
    // public String getState() { return state; } // Represented by listId; actual name resolved by Stories class

    public PlannedTask getiTesk() {
        return iTesk;
    }

    public void setiTesk(PlannedTask iTesk) {
        this.iTesk = iTesk;
    }
}
