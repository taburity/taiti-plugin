package br.edu.ufape.taiti.service;

import br.edu.ufape.taiti.exceptions.HttpException;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

import java.io.File;
import java.util.List;

public class PivotalTracker {
    private static final String PIVOTAL_URL = "https://www.pivotaltracker.com";
    private static final String PROJECT_PATH = "/n/projects/";
    private static final String API_PATH = "/services/v5";
    private static final String TOKEN_HEADER = "X-TrackerToken";
    private static final String TAITI_MSG = "[TAITI] Scenarios";

    private final String token;
    private final String taskID;
    private final String projectID;

    public PivotalTracker(String token, String pivotalProjectURL, String taskID) {
        this.token = token;
        this.taskID = taskID;
        this.projectID = getProjectIDFromProjectURL(pivotalProjectURL);
    }

    // TODO: descobrir como salvar os dados do usuario
    public void saveScenarios(File scenarios) throws HttpException {
        JSONArray comments = getComments();
        JSONObject taitiComment = getTaitiComment(comments);
        if (taitiComment != null) {
            deleteComment(getCommentID(taitiComment));
        }
        postCommentWithFile(scenarios);
    }

    private JSONArray getComments() throws HttpException {
        String request = "/projects/" + projectID + "/stories/" + taskID + "/comments";

        HttpResponse<JsonNode> response = Unirest.get(PIVOTAL_URL + API_PATH + request)
                .header(TOKEN_HEADER, token)
                .asJson();

        if (!response.isSuccess()) {
            throw new HttpException(response.getStatusText(), response.getStatus());
        }

        return response.getBody().getArray();
    }

    private JSONObject getTaitiComment(JSONArray jsonArray) {
        JSONObject jsonComment = null;

        for (Object obj : jsonArray) {
            if (obj instanceof JSONObject) {
                JSONObject json = (JSONObject) obj;
                if (json.get("text").equals(TAITI_MSG)) {
                    jsonComment = json;
                }
            }
        }

        return jsonComment;
    }

    private String getCommentID(JSONObject jsonComment) {
        return String.valueOf(jsonComment.get("id"));
    }

    private void deleteComment(String commentID) throws HttpException {
        String request = "/projects/" + projectID + "/stories/" + taskID + "/comments/" + commentID;

        HttpResponse<JsonNode> response = Unirest.delete(PIVOTAL_URL + API_PATH + request)
                .header(TOKEN_HEADER, token)
                .header("Content-Type", "application/json")
                .asJson();

        if (!response.isSuccess()) {
            throw new HttpException(response.getStatusText(), response.getStatus());
        }
    }

    private void postCommentWithFile(File file) throws HttpException {
        // add file
        String requestFile = "/projects/" + projectID + "/uploads";

        HttpResponse<JsonNode> responseFile = Unirest.post(PIVOTAL_URL + API_PATH + requestFile)
                .header(TOKEN_HEADER, token)
                .field("file", file)
                .asJson();

        if (!responseFile.isSuccess()) {
            throw new HttpException(responseFile.getStatusText(), responseFile.getStatus());
        }

        // add comment
        String requestComment = "/projects/" + projectID + "/stories/" + taskID + "/comments?fields=%3Adefault%2Cfile_attachment_ids";

        JSONObject json = new JSONObject();
        json.put("file_attachments", List.of(responseFile.getBody().getObject()));
        json.put("text", TAITI_MSG);

        HttpResponse<JsonNode> responseComment = Unirest.post(PIVOTAL_URL + API_PATH + requestComment)
                .header(TOKEN_HEADER, token)
                .header("Content-Type", "application/json")
                .body(json)
                .asJson();

        if (!responseComment.isSuccess()) {
            throw new HttpException(responseComment.getStatusText(), responseComment.getStatus());
        }
    }

    private String getProjectIDFromProjectURL(String pivotalProjectURL) {
        return pivotalProjectURL.replace(PIVOTAL_URL + PROJECT_PATH, "");
    }
}
