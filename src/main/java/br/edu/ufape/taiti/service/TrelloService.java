package br.edu.ufape.taiti.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

/**
 * Classe responsável por fazer todas as requisições à API do Trello.
 * Cenários são persistidos no MongoDB via MongoService.
 * O Trello mantém apenas um comentário-marcador ([TAITI] Scenarios) por cartão.
 */
public class TrelloService {

    private static final String TRELLO_API_URL = "https://api.trello.com/1";
    private static final String TAITI_MSG      = "[TAITI] Scenarios";

    private final String        apiKey;
    private final String        serverToken;
    private final String        boardID;
    private final Project       project;
    private final HttpClient    httpClient;
    private final OkHttpClient  okHttpClient;
    private final MongoService  mongoService;

    // ------------------------------------------------------------------
    // Construtor
    // ------------------------------------------------------------------

    /**
     * @param apiKey        Chave da API do Trello.
     * @param serverToken   Token de servidor do Trello.
     * @param boardIDOrURL  ID ou URL completa do quadro (https://trello.com/b/ID/name ou só ID).
     * @param project       Objeto Project do IntelliJ (pode ser null fora do plugin).
     */
    public TrelloService(String apiKey, String serverToken,
                         String boardIDOrURL, Project project) {

        if (apiKey == null || apiKey.trim().isEmpty())
            throw new IllegalArgumentException("API Key não pode ser nula ou vazia.");
        if (serverToken == null || serverToken.trim().isEmpty())
            throw new IllegalArgumentException("Server Token não pode ser nulo ou vazio.");

        this.apiKey       = apiKey;
        this.serverToken  = serverToken;
        this.boardID      = extractBoardIDFromURL(boardIDOrURL);
        this.project      = project;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(45))
                .writeTimeout(Duration.ofSeconds(45))
                .readTimeout(Duration.ofSeconds(45))
                .build();

        // Dotenv: resolve a partir do diretório de trabalho atual, sem hardcode de caminho.
        // Em produção/plugin, defina a variável de ambiente TAITI_MONGO_URI diretamente
        // (sem depender de arquivo .env) para evitar problemas de caminho.
        Dotenv dotenv = Dotenv.configure()
                .directory("D:/TCC/taiti-plugin")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        String mongoUri = dotenv.get("TAITI_MONGO_URI");
        if (mongoUri == null || mongoUri.trim().isEmpty())
            throw new IllegalStateException(
                    "TAITI_MONGO_URI não encontrada. "
                            + "Defina a variável de ambiente ou crie um arquivo .env "
                            + "no diretório de trabalho do processo.");

        this.mongoService = new MongoService(mongoUri);
    }

    // ------------------------------------------------------------------
    // Métodos públicos principais
    // ------------------------------------------------------------------

    /**
     * Salva o arquivo de cenários no MongoDB e marca o cartão no Trello
     * com um comentário-marcador. Qualquer dado anterior para o mesmo cardID
     * é removido primeiro (Mongo + comentário Trello).
     *
     * @param scenarios Arquivo CSV de cenários.
     * @param cardID    ID do cartão Trello.
     */
    public void saveTaitiScenarios(File scenarios, String cardID)
            throws TrelloApiException, IOException {

        if (scenarios == null || !scenarios.exists())
            throw new IOException("Arquivo de cenários não encontrado ou inválido.");
        if (cardID == null || cardID.trim().isEmpty())
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");

        // 1. Remove dados antigos (Mongo + comentário marcador do Trello)
        deleteTaitiScenarios(cardID);

        // 2. Persiste no MongoDB e posta o comentário marcador
        postTaitiCommentAndUploadFile(scenarios, cardID, TAITI_MSG);
    }

    /**
     * Remove o cenário do MongoDB e o comentário-marcador do Trello para o cartão.
     *
     * @param cardID ID do cartão Trello.
     */
    public void deleteTaitiScenarios(String cardID)
            throws TrelloApiException, IOException {

        if (cardID == null || cardID.trim().isEmpty())
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");

        // 1. Remove do MongoDB
        try {
            mongoService.deleteScenario(cardID);
        } catch (Exception e) {
            // Loga, mas não interrompe: mesmo que o Mongo falhe, tenta limpar o Trello.
            System.err.println("Aviso: falha ao deletar cenário do MongoDB para o cartão "
                    + cardID + ": " + e.getMessage());
        }

        // 2. Remove comentários marcadores do Trello
        try {
            JSONArray comments = getCommentsOnCard(cardID);
            for (int i = 0; i < comments.length(); i++) {
                JSONObject action = comments.getJSONObject(i);
                if (action.has("data")
                        && action.getJSONObject("data").has("text")
                        && TAITI_MSG.equals(action.getJSONObject("data").getString("text"))) {
                    deleteCommentAction(getActionID(action));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operação interrompida ao remover comentário TAITI.", e);
        } catch (JSONException e) {
            throw new TrelloApiException(
                    "Erro ao processar JSON durante deleção de cenários: " + e.getMessage(), -1, e);
        }
    }

    /**
     * Baixa o arquivo de cenários do MongoDB para o diretório temporário do projeto.
     *
     * @param cardID ID do cartão Trello.
     * @return Arquivo baixado ou {@code null} se não houver cenário salvo.
     */
    public File downloadTaitiFileFromCard(String cardID)
            throws TrelloApiException, IOException {

        if (cardID == null || cardID.trim().isEmpty())
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");

        String basePath = getProjectPath();

        try {
            File file = mongoService.downloadScenario(cardID, basePath);
            if (file == null)
                System.out.println("Nenhum cenário encontrado no MongoDB para o cartão: " + cardID);
            return file;
        } catch (Exception e) {
            throw new TrelloApiException(
                    "Falha ao baixar cenário do MongoDB para o cartão " + cardID
                            + ": " + e.getMessage(), -1, e);
        }
    }

    /**
     * Retorna detalhes dos cenários TAITI presentes em cartões das listas indicadas.
     * Consulta o MongoDB para verificar existência do cenário (não mais os anexos do Trello).
     *
     * @param plannedListIds IDs das listas "planejadas" do Trello.
     * @return JSONArray com {cardId, cardName, hasTaitiScenario}.
     */
    public JSONArray getTaitiFilesDetailsFromLists(List<String> plannedListIds)
            throws TrelloApiException, IOException {

        if (plannedListIds == null || plannedListIds.isEmpty())
            throw new IllegalArgumentException(
                    "Lista de IDs de listas planejadas não pode ser nula ou vazia.");

        JSONArray result = new JSONArray();
        try {
            for (String listId : plannedListIds) {
                JSONArray cards = getCardsFromList(listId);
                for (int i = 0; i < cards.length(); i++) {
                    JSONObject card  = cards.getJSONObject(i);
                    String    cardId = card.getString("id");

                    // Verifica existência do comentário marcador no Trello
                    boolean hasMarker = (getTaitiCommentActionOnCard(cardId) != null);
                    if (!hasMarker) continue;

                    // Verifica existência no MongoDB
                    boolean hasScenario;
                    try {
                        hasScenario = mongoService.scenarioExists(cardId);
                    } catch (Exception e) {
                        System.err.println("Aviso: não foi possível verificar MongoDB para cartão "
                                + cardId + ": " + e.getMessage());
                        hasScenario = false;
                    }

                    JSONObject detail = new JSONObject();
                    detail.put("cardId",           cardId);
                    detail.put("cardName",          card.getString("name"));
                    detail.put("hasTaitiScenario",  hasScenario);
                    result.put(detail);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operação interrompida ao buscar detalhes TAITI.", e);
        } catch (JSONException e) {
            throw new TrelloApiException(
                    "Erro ao processar JSON ao buscar detalhes TAITI: " + e.getMessage(), -1, e);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Métodos de leitura — Board / Lists / Cards
    // ------------------------------------------------------------------

    public int checkBoardStatus() throws IOException, InterruptedException, TrelloApiException {
        if (this.boardID == null || this.boardID.trim().isEmpty()) {
            System.err.println("ID do Quadro não configurado.");
            return 400;
        }
        String endpoint = "/boards/" + this.boardID + buildAuthQueryString(true);
        try {
            return makeApiRequest("GET", endpoint, null).statusCode();
        } catch (IOException | InterruptedException e) {
            throw new TrelloApiException(
                    "Falha na comunicação ao verificar status do quadro: " + e.getMessage(), -1, e);
        }
    }

    public JSONObject getListDetails(String listId)
            throws TrelloApiException, IOException, InterruptedException {

        if (listId == null || listId.trim().isEmpty())
            throw new IllegalArgumentException("O ID da lista não pode ser nulo ou vazio.");

        String endpoint = "/lists/" + listId + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try { return new JSONObject(response.body()); }
            catch (JSONException e) {
                throw new TrelloApiException(
                        "Erro ao parsear JSON dos detalhes da lista: " + e.getMessage(),
                        response.statusCode(), e);
            }
        }
        throw new TrelloApiException(
                "Falha ao buscar detalhes da lista: " + response.body(), response.statusCode());
    }

    public JSONArray getCardsOnBoard() throws TrelloApiException, IOException, InterruptedException {
        if (this.boardID == null || this.boardID.trim().isEmpty())
            throw new IllegalStateException("BoardID não configurado.");

        String endpoint = "/boards/" + this.boardID + "/cards"
                + buildAuthQueryString(true)
                + "&fields=id,name,idList,idMembers,desc,url,labels,due,closed";
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try { return new JSONArray(response.body()); }
            catch (JSONException e) {
                throw new TrelloApiException(
                        "Erro ao parsear JSON dos cartões do quadro: " + e.getMessage(),
                        response.statusCode(), e);
            }
        }
        throw new TrelloApiException(
                "Falha ao buscar cartões do quadro: " + response.body(), response.statusCode());
    }

    public JSONArray getCardsFromList(String listID)
            throws TrelloApiException, IOException, InterruptedException {

        if (listID == null || listID.trim().isEmpty())
            throw new IllegalArgumentException("ID da Lista não pode ser nulo ou vazio.");

        String endpoint = "/lists/" + listID + "/cards"
                + buildAuthQueryString(true)
                + "&fields=id,name,idList,idMembers,desc,url,labels,due,closed";
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try { return new JSONArray(response.body()); }
            catch (JSONException e) {
                throw new TrelloApiException(
                        "Erro ao parsear JSON dos cartões da lista: " + e.getMessage(),
                        response.statusCode(), e);
            }
        }
        throw new TrelloApiException(
                "Falha ao buscar cartões da lista: " + response.body(), response.statusCode());
    }

    public JSONArray getBoardMembers() throws TrelloApiException, IOException, InterruptedException {
        if (this.boardID == null || this.boardID.trim().isEmpty())
            throw new IllegalArgumentException("ID do Quadro não configurado para buscar membros.");

        String endpoint = "/boards/" + this.boardID + "/members" + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try { return new JSONArray(response.body()); }
            catch (JSONException e) {
                throw new TrelloApiException(
                        "Erro ao parsear JSON dos membros do quadro: " + e.getMessage(),
                        response.statusCode(), e);
            }
        }
        throw new TrelloApiException(
                "Falha ao buscar membros do quadro: " + response.body(), response.statusCode());
    }

    public String getAuthenticatedUserId()
            throws TrelloApiException, IOException, InterruptedException {

        String endpoint = "/members/me" + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try { return new JSONObject(response.body()).getString("id"); }
            catch (JSONException e) {
                throw new TrelloApiException(
                        "Erro ao parsear JSON do ID do usuário: " + e.getMessage(),
                        response.statusCode(), e);
            }
        }
        throw new TrelloApiException(
                "Falha ao buscar ID do usuário autenticado: " + response.body(), response.statusCode());
    }

    // ------------------------------------------------------------------
    // Métodos de leitura — Comentários / Anexos
    // ------------------------------------------------------------------

    public JSONArray getCommentsOnCard(String cardID)
            throws TrelloApiException, IOException, InterruptedException {

        if (cardID == null || cardID.trim().isEmpty())
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");

        String endpoint = "/cards/" + cardID + "/actions"
                + buildAuthQueryString(true) + "&filter=commentCard";
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try { return new JSONArray(response.body()); }
            catch (JSONException e) {
                throw new TrelloApiException(
                        "Erro ao parsear JSON dos comentários: " + e.getMessage(),
                        response.statusCode(), e);
            }
        }
        throw new TrelloApiException(
                "Falha ao buscar comentários do cartão: " + response.body(), response.statusCode());
    }

    public JSONObject getTaitiCommentActionOnCard(String cardID)
            throws TrelloApiException, IOException, InterruptedException {

        JSONArray comments = getCommentsOnCard(cardID);
        for (int i = 0; i < comments.length(); i++) {
            try {
                JSONObject action = comments.getJSONObject(i);
                if (action.has("data")
                        && action.getJSONObject("data").has("text")
                        && TAITI_MSG.equals(action.getJSONObject("data").getString("text")))
                    return action;
            } catch (JSONException e) {
                System.err.println("Aviso: erro ao parsear comentário no cartão "
                        + cardID + ": " + e.getMessage());
            }
        }
        return null;
    }

    /** Mantido para compatibilidade; ainda é usado por deleteTaitiScenarios(). */
    public JSONArray getCardAttachments(String cardID)
            throws TrelloApiException, IOException, InterruptedException {

        if (cardID == null || cardID.trim().isEmpty())
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");

        String endpoint = "/cards/" + cardID + "/attachments" + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try { return new JSONArray(response.body()); }
            catch (JSONException e) {
                throw new TrelloApiException(
                        "Erro ao parsear JSON dos anexos: " + e.getMessage(),
                        response.statusCode(), e);
            }
        }
        throw new TrelloApiException(
                "Falha ao buscar anexos do cartão: " + response.body(), response.statusCode());
    }

    // ------------------------------------------------------------------
    // Métodos de escrita — Comentários / Anexos
    // ------------------------------------------------------------------

    /**
     * Persiste o cenário no MongoDB e posta o comentário-marcador no Trello.
     * Se o comentário falhar, remove o dado recém-salvo no Mongo (rollback).
     */
    public void postTaitiCommentAndUploadFile(File file, String cardID, String commentText)
            throws TrelloApiException, IOException {

        if (file == null || !file.exists())
            throw new IOException("Arquivo inválido ou não encontrado para upload.");
        if (cardID == null || cardID.trim().isEmpty())
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");
        if (commentText == null || commentText.trim().isEmpty())
            throw new IllegalArgumentException("Texto do comentário não pode ser nulo ou vazio.");

        // 1. Salva no MongoDB
        try {
            mongoService.saveScenario(file, cardID);
        } catch (Exception e) {
            throw new TrelloApiException(
                    "Falha ao salvar cenário no MongoDB para o cartão " + cardID
                            + ": " + e.getMessage(), -1, e);
        }

        // 2. Posta comentário marcador no Trello
        try {
            postComment(cardID, commentText);
        } catch (IOException e) {
            // Rollback: remove o dado que acabou de ser salvo no Mongo
            System.err.println(
                    "Comentário Trello falhou após salvar no MongoDB. "
                            + "Iniciando rollback para o cartão: " + cardID);
            try {
                mongoService.deleteScenario(cardID);
            } catch (Exception rollbackEx) {
                System.err.println(
                        "ATENÇÃO: rollback do MongoDB falhou para o cartão " + cardID
                                + ": " + rollbackEx.getMessage());
            }
            throw e; // relança a exceção original
        }

        System.out.println("Cenário salvo no MongoDB e comentário postado para o cartão: " + cardID);
    }

    private void postComment(String cardID, String commentText)
            throws TrelloApiException, IOException {

        String encodedText = URLEncoder.encode(commentText, StandardCharsets.UTF_8);
        String url = TRELLO_API_URL
                + "/cards/" + cardID + "/actions/comments"
                + buildAuthQueryString(true)
                + "&text=" + encodedText;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new TrelloApiException(
                        "Falha ao postar comentário: " + response.body(), response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operação de postagem de comentário interrompida.", e);
        }
    }

    public void deleteCommentAction(String commentActionID)
            throws TrelloApiException, IOException, InterruptedException {

        if (commentActionID == null || commentActionID.trim().isEmpty())
            throw new IllegalArgumentException(
                    "ID da Ação do Comentário não pode ser nulo ou vazio.");

        String endpoint = "/actions/" + commentActionID + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("DELETE", endpoint, null);

        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new TrelloApiException(
                    "Falha ao deletar comentário: " + response.body(), response.statusCode());
    }

    public void deleteAttachment(String cardId, String attachmentId)
            throws TrelloApiException, IOException, InterruptedException {

        String endpoint = "/cards/" + cardId + "/attachments/" + attachmentId
                + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("DELETE", endpoint, null);

        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new TrelloApiException(
                    "Falha ao deletar anexo: " + response.body(), response.statusCode());
    }

    // ------------------------------------------------------------------
    // Exceção personalizada
    // ------------------------------------------------------------------

    public static class TrelloApiException extends IOException {
        private final int statusCode;

        public TrelloApiException(String message, int statusCode) {
            super(message + " (Status: " + statusCode + ")");
            this.statusCode = statusCode;
        }

        public TrelloApiException(String message, int statusCode, Throwable cause) {
            super(message + " (Status: " + statusCode + ")", cause);
            this.statusCode = statusCode;
        }

        public int getStatusCode() { return statusCode; }
    }

    // ------------------------------------------------------------------
    // Helpers privados
    // ------------------------------------------------------------------

    private String buildAuthQueryString(boolean firstParam) {
        return (firstParam ? "?" : "&") + "key=" + apiKey + "&token=" + serverToken;
    }

    private String extractBoardIDFromURL(String boardURLOrID) {
        if (boardURLOrID == null || boardURLOrID.trim().isEmpty())
            throw new IllegalArgumentException("Board URL ou ID não pode ser nulo ou vazio.");

        java.util.regex.Pattern p =
                java.util.regex.Pattern.compile(
                        "^https?://trello\\.com/b/([a-zA-Z0-9]+)(?:/[^/?#]+)*$");
        java.util.regex.Matcher m = p.matcher(boardURLOrID);
        if (m.matches()) return m.group(1);

        if (boardURLOrID.matches("^[a-zA-Z0-9]+$")) return boardURLOrID;

        throw new IllegalArgumentException(
                "URL ou ID do quadro inválido: " + boardURLOrID
                        + ". Esperado: https://trello.com/b/BOARD_ID/nome ou apenas BOARD_ID.");
    }

    private HttpResponse<String> makeApiRequest(String method, String endpoint, String body)
            throws IOException, InterruptedException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(TRELLO_API_URL + endpoint))
                .header("Accept", "application/json");

        if (body != null && !body.isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
            builder.header("Content-Type", "application/json; charset=utf-8");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String getActionID(JSONObject actionJson) throws JSONException {
        if (actionJson == null)
            throw new IllegalArgumentException("JSON da ação não pode ser nulo.");
        return actionJson.getString("id");
    }

    private String getProjectPath() {
        if (project == null)
            return System.getProperty("java.io.tmpdir");
        VirtualFile dir = ProjectUtil.guessProjectDir(project);
        if (dir != null) return dir.getPath();
        String base = project.getBasePath();
        if (base != null) return base;
        System.err.println("Caminho do projeto indeterminado. Usando diretório temporário.");
        return System.getProperty("java.io.tmpdir");
    }
}
