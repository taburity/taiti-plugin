package br.edu.ufape.taiti.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Classe responsável por fazer todas as requisições à API do Trello.
 * Adapta funcionalidades originalmente pensadas para o Pivotal Tracker.
 */
public class TrelloService {
    private static final String TRELLO_API_URL = "https://api.trello.com/1";
    private static final String TAITI_MSG = "[TAITI] Scenarios";

    private final String apiKey;
    private final String serverToken;
    private final String boardID;
    private final Project project; // Objeto Project do IntelliJ
    private final HttpClient httpClient;
    private final OkHttpClient okHttpClient;

    /**
     * Construtor da classe TrelloService.
     *
     * @param apiKey Chave da API do Trello.
     * @param serverToken Token de servidor do Trello.
     * @param boardIDOrURL ID ou URL completa do quadro do Trello.
     * @param project Objeto Project do IntelliJ (usado para obter o caminho do projeto local).
     */
    public TrelloService(String apiKey, String serverToken, String boardIDOrURL, Project project) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key não pode ser nula ou vazia.");
        }
        if (serverToken == null || serverToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Server Token não pode ser nulo ou vazio.");
        }
        this.apiKey = apiKey;
        this.serverToken = serverToken;
        this.boardID = extractBoardIDFromURL(boardIDOrURL); // Pode lançar IllegalArgumentException
        this.project = project; // Pode ser nulo se não estiver em um contexto de projeto IntelliJ
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20)) // Aumentado timeout
                .build();
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(45)) // Aumentado timeout
                .writeTimeout(Duration.ofSeconds(45))  // Aumentado timeout
                .readTimeout(Duration.ofSeconds(45))   // Aumentado timeout
                .build();
    }

    private String buildAuthQueryString(boolean firstParam) {
        return (firstParam ? "?" : "&") + "key=" + this.apiKey + "&token=" + this.serverToken;
    }

    private String extractBoardIDFromURL(String boardURLOrID) {
        if (boardURLOrID == null || boardURLOrID.trim().isEmpty()) {
            throw new IllegalArgumentException("Board URL ou ID não pode ser nulo ou vazio.");
        }
        // Exemplo: https://trello.com/b/boardId/board-name
        // Regex para extrair ID de uma URL do Trello
        java.util.regex.Pattern trelloBoardUrlPattern = java.util.regex.Pattern.compile("^https?://trello\\.com/b/([a-zA-Z0-9]+)(?:/[^/?#]+)*$");
        java.util.regex.Matcher matcher = trelloBoardUrlPattern.matcher(boardURLOrID);
        if (matcher.matches()) {
            return matcher.group(1); // Retorna o ID do quadro capturado
        }

        // Assume que já é um ID se não corresponder ao padrão de URL e for alfanumérico
        if (boardURLOrID.matches("^[a-zA-Z0-9]+$")) {
            return boardURLOrID;
        }
        throw new IllegalArgumentException("URL ou ID do quadro inválido: " + boardURLOrID + ". Formato esperado: https://trello.com/b/BOARD_ID/board-name ou apenas BOARD_ID.");
    }

    /**
     * Busca os detalhes de uma lista específica do Trello.
     *
     * @param listId O ID da lista a ser buscada.
     * @return Um JSONObject contendo os detalhes da lista.
     * @throws TrelloApiException Se ocorrer um erro na comunicação com a API do Trello.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a thread for interrompida durante a requisição.
     */
    public JSONObject getListDetails(String listId) throws TrelloApiException, IOException, InterruptedException {
        if (listId == null || listId.trim().isEmpty()) {
            throw new IllegalArgumentException("O ID da lista não pode ser nulo ou vazio.");
        }

        String endpoint = "/lists/" + listId + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try {
                return new JSONObject(response.body());
            } catch (JSONException e) {
                throw new TrelloApiException("Erro ao parsear JSON da resposta para detalhes da lista: " + e.getMessage(), response.statusCode(), e);
            }
        } else {
            throw new TrelloApiException("Falha ao buscar detalhes da lista: " + response.body(), response.statusCode());
        }
    }

    /**
     * Busca todos os cartões em um quadro (board) específico do Trello.
     *
     * @return JSONArray contendo todos os cartões do quadro.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     */
    public JSONArray getCardsOnBoard() throws TrelloApiException, IOException, InterruptedException {
        if (this.boardID == null || this.boardID.trim().isEmpty()) {
            throw new IllegalStateException("ID do Quadro (BoardID) não foi configurado ou é inválido.");
        }
        // Endpoint para buscar todos os cartões de um quadro
        // Você pode adicionar mais parâmetros como fields=name,idList,idMembers se quiser limitar os dados retornados
        String endpoint = "/boards/" + this.boardID + "/cards" + buildAuthQueryString(true) + "&fields=id,name,idList,idMembers,desc,url,labels,due,closed";
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try {
                return new JSONArray(response.body());
            } catch (JSONException e) {
                throw new TrelloApiException("Erro ao parsear JSON da resposta para cartões do quadro: " + e.getMessage(), response.statusCode(), e);
            }
        } else {
            throw new TrelloApiException("Falha ao buscar cartões do quadro: " + response.body(), response.statusCode());
        }
    }


    /**
     * Exceção personalizada para erros da API do Trello.
     */
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

        public int getStatusCode() {
            return statusCode;
        }
    }

    private HttpResponse<String> makeApiRequest(String method, String endpoint, String body) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(TRELLO_API_URL + endpoint))
                .header("Accept", "application/json");

        if (body != null && !body.isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
            requestBuilder.header("Content-Type", "application/json; charset=utf-8");
            requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpRequest request = requestBuilder.build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }


    /**
     * Salva os cenários (arquivo) em um cartão do Trello.
     * Primeiro, remove qualquer comentário TAITI antigo com seu anexo associado (se a lógica permitir).
     * Depois, faz upload do novo arquivo e posta um novo comentário TAITI.
     *
     * @param scenarios Arquivo de cenários a ser salvo.
     * @param cardID ID do cartão do Trello.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     */
    public void saveTaitiScenarios(File scenarios, String cardID) throws TrelloApiException, IOException {
        if (scenarios == null || !scenarios.exists()) {
            throw new IOException("Arquivo de cenários não encontrado ou é inválido.");
        }
        if (cardID == null || cardID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");
        }

        // 1. Tenta encontrar e deletar comentários TAITI antigos e seus anexos (lógica simplificada)
        deleteTaitiScenarios(cardID); // deleteTaitiScenarios agora lida com a deleção de comentários e anexos relacionados

        // 2. Faz upload do novo arquivo e posta o comentário TAITI
        postTaitiCommentAndUploadFile(scenarios, cardID, TAITI_MSG);
    }

    /**
     * Deleta comentários TAITI e anexos associados de um cartão.
     * Esta é uma simplificação: Trello não associa diretamente anexos a comentários.
     * Este método deletará comentários com TAITI_MSG e tentará deletar anexos com nomes "suspeitos".
     *
     * @param cardID ID do cartão do Trello.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     */
    public void deleteTaitiScenarios(String cardID) throws TrelloApiException, IOException {
        if (cardID == null || cardID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");
        }
        try {
            JSONArray comments = getCommentsOnCard(cardID);
            for (int i = 0; i < comments.length(); i++) {
                JSONObject commentAction = comments.getJSONObject(i);
                if (commentAction.has("data") && commentAction.getJSONObject("data").has("text")) {
                    String commentText = commentAction.getJSONObject("data").getString("text");
                    if (TAITI_MSG.equals(commentText)) {
                        deleteCommentAction(getActionID(commentAction));
                    }
                }
            }

            // Opcional: Deletar anexos que parecem ser de TAITI (ex: nome do arquivo .csv)
            JSONArray attachments = getCardAttachments(cardID);
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.getJSONObject(i);
                String fileName = attachment.optString("name", "").toLowerCase(); // Usar optString para segurança
                // Verifica se o nome do arquivo contém "taiti" ou "scenario" e termina com .csv
                if ((fileName.contains("taiti") || fileName.contains("scenario")) && fileName.endsWith(".csv")) {
                    System.out.println("Tentando deletar anexo TAITI: " + fileName + " (ID: " + attachment.getString("id") + ")");
                    deleteAttachment(cardID, attachment.getString("id"));
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operação interrompida ao deletar cenários do Trello.", e);
        } catch (JSONException e) {
            throw new TrelloApiException("Erro ao processar JSON durante a deleção de cenários: " + e.getMessage(), -1, e);
        }
    }

    /**
     * Deleta um anexo específico de um cartão.
     * @param cardId ID do cartão.
     * @param attachmentId ID do anexo.
     * @throws TrelloApiException Em caso de erro na API.
     * @throws IOException Em caso de erro de I/O.
     * @throws InterruptedException Se a thread for interrompida.
     */
    public void deleteAttachment(String cardId, String attachmentId) throws TrelloApiException, IOException, InterruptedException {
        String endpoint = "/cards/" + cardId + "/attachments/" + attachmentId + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("DELETE", endpoint, null);

        if (response.statusCode() < 200 || response.statusCode() >= 300) { // 204 (No Content) é sucesso para DELETE
            throw new TrelloApiException("Falha ao deletar anexo: " + response.body(), response.statusCode());
        }
        System.out.println("Anexo deletado com sucesso: " + attachmentId + " do cartão " + cardId);
    }


    /**
     * Verifica o status do quadro (Board) no Trello.
     *
     * @return Código de status HTTP da resposta.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     * @throws TrelloApiException Se a API do Trello retornar um erro.
     */
    public int checkBoardStatus() throws IOException, InterruptedException, TrelloApiException {
        if (this.boardID == null || this.boardID.trim().isEmpty()) {
            System.err.println("ID do Quadro não configurado.");
            return 400; // Bad Request (ou outro código apropriado)
        }
        String endpoint = "/boards/" + this.boardID + buildAuthQueryString(true);
        HttpResponse<String> response;
        try {
            response = makeApiRequest("GET", endpoint, null);
        } catch (IOException | InterruptedException e) {
            // Envolve a exceção original para não perder a causa raiz
            throw new TrelloApiException("Falha na comunicação ao verificar status do quadro: " + e.getMessage(), -1, e);
        }

        // Não lançar exceção para todos os status != 200 aqui, pois o chamador pode querer tratar diferentes status.
        // Apenas retornar o status. Se o corpo da resposta for relevante para o erro, TrelloApiException pode ser lançada por makeApiRequest ou pelo chamador.
        return response.statusCode();
    }

    /**
     * Baixa o arquivo TAITI (primeiro .csv encontrado) de um cartão, se houver um comentário TAITI.
     *
     * @param cardID ID do cartão do Trello.
     * @return O arquivo baixado, ou null se não encontrado.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     */
    public File downloadTaitiFileFromCard(String cardID) throws TrelloApiException, IOException {
        if (cardID == null || cardID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");
        }
        try {
            JSONObject taitiComment = getTaitiCommentActionOnCard(cardID);
            if (taitiComment == null) {
                System.out.println("Nenhum comentário TAITI encontrado no cartão: " + cardID);
                return null;
            }

            JSONArray attachments = getCardAttachments(cardID);
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.getJSONObject(i);
                String fileName = attachment.optString("fileName", "").toLowerCase(); // Usar optString e toLowerCase
                // Procura por um anexo .csv como uma convenção para arquivo de cenário
                if (fileName.endsWith(".csv")) { // Simplificado para qualquer .csv por enquanto
                    String downloadUrl = attachment.getString("url"); // URL direta do Trello para download

                    // Cria diretório temporário se não existir
                    String projectBasePath = getProjectPath();
                    if (projectBasePath == null || projectBasePath.isEmpty()) {
                        projectBasePath = System.getProperty("java.io.tmpdir"); // Fallback para diretório temp do sistema
                    }
                    Path tempTaitiDirPath = Paths.get(projectBasePath, "temp_taiti");
                    if (!Files.exists(tempTaitiDirPath)) {
                        Files.createDirectories(tempTaitiDirPath);
                    }
                    // Usar um nome de arquivo mais seguro, evitando caracteres inválidos de 'fileName'
                    String safeFileName = "taiti_download_" + cardID + "_" + attachment.optString("id", "file") + ".csv";
                    File downloadedFile = new File(tempTaitiDirPath.toFile(), safeFileName);

                    // Realiza o download usando o token da API no header para autenticação
                    // A URL de download do Trello já é pré-assinada e geralmente não requer headers de auth adicionais.
                    HttpRequest downloadRequest = HttpRequest.newBuilder()
                            .uri(URI.create(downloadUrl))
                            // .header("Authorization", "OAuth oauth_consumer_key=\"" + apiKey + "\", oauth_token=\"" + serverToken + "\"") // Geralmente não necessário para URL de download do Trello
                            .GET()
                            .build();

                    HttpResponse<byte[]> response = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray());

                    if (response.statusCode() == 200) {
                        try (FileOutputStream fos = new FileOutputStream(downloadedFile)) {
                            fos.write(response.body());
                        }
                        System.out.println("Arquivo baixado: " + downloadedFile.getAbsolutePath());
                        return downloadedFile;
                    } else {
                        // Tenta com redirecionamento (alguns downloads do Trello podem redirecionar)
                        if (response.statusCode() >= 300 && response.statusCode() < 400 && response.headers().firstValue("Location").isPresent()) {
                            String redirectUrl = response.headers().firstValue("Location").get();
                            HttpRequest redirectDownloadRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(redirectUrl))
                                    .GET()
                                    .build();
                            response = httpClient.send(redirectDownloadRequest, HttpResponse.BodyHandlers.ofByteArray());
                            if (response.statusCode() == 200) {
                                try (FileOutputStream fos = new FileOutputStream(downloadedFile)) {
                                    fos.write(response.body());
                                }
                                System.out.println("Arquivo baixado (após redirecionamento): " + downloadedFile.getAbsolutePath());
                                return downloadedFile;
                            } else {
                                throw new TrelloApiException("Falha ao baixar arquivo (após redirecionamento): " + response.statusCode() + " - " + new String(response.body(), StandardCharsets.UTF_8), response.statusCode());
                            }
                        } else {
                            throw new TrelloApiException("Falha ao baixar arquivo: " + response.statusCode() + " - " + new String(response.body(), StandardCharsets.UTF_8), response.statusCode());
                        }
                    }
                }
            }
            System.out.println("Nenhum anexo .csv encontrado no cartão " + cardID + " após encontrar comentário TAITI.");
            return null;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operação interrompida ao baixar arquivo do Trello.", e);
        } catch (JSONException e) {
            throw new TrelloApiException("Erro ao processar JSON durante download: " + e.getMessage(), -1, e);
        }
    }


    /**
     * Busca detalhes de arquivos TAITI (anexos .csv) em cartões de listas especificadas.
     *
     * @param plannedListIds Lista de IDs das listas do Trello consideradas "planejadas".
     * @return JSONArray com informações dos arquivos encontrados (cardId, attachmentName, attachmentUrl).
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     */
    public JSONArray getTaitiFilesDetailsFromLists(List<String> plannedListIds) throws TrelloApiException, IOException {
        if (plannedListIds == null || plannedListIds.isEmpty()) {
            throw new IllegalArgumentException("Lista de IDs de listas planejadas não pode ser nula ou vazia.");
        }
        JSONArray taitiFilesDetails = new JSONArray();
        try {
            for (String listId : plannedListIds) {
                JSONArray cards = getCardsFromList(listId);
                for (int i = 0; i < cards.length(); i++) {
                    JSONObject card = cards.getJSONObject(i);
                    String cardId = card.getString("id");

                    if (getTaitiCommentActionOnCard(cardId) != null) {
                        JSONArray attachments = getCardAttachments(cardId);
                        for (int j = 0; j < attachments.length(); j++) {
                            JSONObject attachment = attachments.getJSONObject(j);
                            if (attachment.optString("fileName", "").toLowerCase().endsWith(".csv")) {
                                JSONObject fileDetail = new JSONObject();
                                fileDetail.put("cardId", cardId);
                                fileDetail.put("cardName", card.getString("name"));
                                fileDetail.put("attachmentId", attachment.getString("id"));
                                fileDetail.put("attachmentName", attachment.getString("fileName"));
                                fileDetail.put("attachmentUrl", attachment.getString("url")); // URL direta de download
                                taitiFilesDetails.put(fileDetail);
                                break; // Pega o primeiro .csv encontrado por cartão
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operação interrompida ao buscar detalhes de arquivos TAITI.", e);
        } catch (JSONException e) {
            throw new TrelloApiException("Erro ao processar JSON ao buscar detalhes de arquivos TAITI: " + e.getMessage(), -1, e);
        }
        return taitiFilesDetails;
    }


    /**
     * Busca todos os anexos de um cartão.
     *
     * @param cardID ID do cartão.
     * @return JSONArray com os anexos.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     */
    public JSONArray getCardAttachments(String cardID) throws TrelloApiException, IOException, InterruptedException {
        if (cardID == null || cardID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");
        }
        String endpoint = "/cards/" + cardID + "/attachments" + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try {
                return new JSONArray(response.body());
            } catch (JSONException e) {
                throw new TrelloApiException("Erro ao parsear JSON da resposta para anexos do cartão: " + e.getMessage(), response.statusCode(), e);
            }
        } else {
            throw new TrelloApiException("Falha ao buscar anexos do cartão: " + response.body(), response.statusCode());
        }
    }

    /**
     * Busca um comentário TAITI em um cartão.
     *
     * @param cardID ID do cartão.
     * @return JSONObject da ação do comentário TAITI, ou null se não encontrado.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     */
    public JSONObject getTaitiCommentActionOnCard(String cardID) throws TrelloApiException, IOException, InterruptedException {
        JSONArray comments = getCommentsOnCard(cardID); // Pode lançar TrelloApiException
        for (int i = 0; i < comments.length(); i++) {
            try {
                JSONObject commentAction = comments.getJSONObject(i);
                if (commentAction.has("data") && commentAction.getJSONObject("data").has("text")) {
                    String commentText = commentAction.getJSONObject("data").getString("text");
                    if (TAITI_MSG.equals(commentText)) {
                        return commentAction;
                    }
                }
            } catch (JSONException e) {
                System.err.println("Aviso: Erro ao parsear um comentário individual no cartão " + cardID + ": " + e.getMessage());
                // Continuar para o próximo comentário em vez de falhar toda a operação
            }
        }
        return null;
    }

    /**
     * Busca todos os comentários (ações do tipo 'commentCard') de um cartão.
     *
     * @param cardID ID do cartão.
     * @return JSONArray com as ações de comentário.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     */
    public JSONArray getCommentsOnCard(String cardID) throws TrelloApiException, IOException, InterruptedException {
        if (cardID == null || cardID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");
        }
        String endpoint = "/cards/" + cardID + "/actions" + buildAuthQueryString(true) + "&filter=commentCard";
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try {
                return new JSONArray(response.body());
            } catch (JSONException e) {
                throw new TrelloApiException("Erro ao parsear JSON da resposta para comentários do cartão: " + e.getMessage(), response.statusCode(), e);
            }
        } else {
            throw new TrelloApiException("Falha ao buscar comentários do cartão: " + response.body(), response.statusCode());
        }
    }

    /**
     * Busca o ID do usuário autenticado.
     *
     * @return ID do usuário.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     */
    public String getAuthenticatedUserId() throws TrelloApiException, IOException, InterruptedException {
        String endpoint = "/members/me" + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try {
                JSONObject me = new JSONObject(response.body());
                return me.getString("id");
            } catch (JSONException e) {
                throw new TrelloApiException("Erro ao parsear JSON da resposta para ID do usuário: " + e.getMessage(), response.statusCode(), e);
            }
        } else {
            throw new TrelloApiException("Falha ao buscar ID do usuário autenticado: " + response.body(), response.statusCode());
        }
    }

    /**
     * Busca os membros de um quadro.
     *
     * @return JSONArray com os membros.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     */
    public JSONArray getBoardMembers() throws TrelloApiException, IOException, InterruptedException {
        if (this.boardID == null || this.boardID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID do Quadro não configurado para buscar membros.");
        }
        String endpoint = "/boards/" + this.boardID + "/members" + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try {
                return new JSONArray(response.body());
            } catch (JSONException e) {
                throw new TrelloApiException("Erro ao parsear JSON da resposta para membros do quadro: " + e.getMessage(), response.statusCode(), e);
            }
        } else {
            throw new TrelloApiException("Falha ao buscar membros do quadro: " + response.body(), response.statusCode());
        }
    }

    /**
     * Busca todos os cartões de uma lista específica.
     *
     * @param listID ID da lista.
     * @return JSONArray com os cartões.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     */
    public JSONArray getCardsFromList(String listID) throws TrelloApiException, IOException, InterruptedException {
        if (listID == null || listID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID da Lista não pode ser nulo ou vazio.");
        }
        // Adiciona fields para obter mais detalhes dos cartões se necessário
        String endpoint = "/lists/" + listID + "/cards" + buildAuthQueryString(true) + "&fields=id,name,idList,idMembers,desc,url,labels,due,closed";
        HttpResponse<String> response = makeApiRequest("GET", endpoint, null);

        if (response.statusCode() == 200) {
            try {
                return new JSONArray(response.body());
            } catch (JSONException e) {
                throw new TrelloApiException("Erro ao parsear JSON da resposta para cartões da lista: " + e.getMessage(), response.statusCode(), e);
            }
        } else {
            throw new TrelloApiException("Falha ao buscar cartões da lista: " + response.body(), response.statusCode());
        }
    }

    /**
     * Faz upload de um arquivo para um cartão e posta um comentário.
     *
     * @param file Arquivo a ser enviado.
     * @param cardID ID do cartão.
     * @param commentText Texto do comentário.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     */
    public void postTaitiCommentAndUploadFile(File file, String cardID, String commentText) throws TrelloApiException, IOException {
        if (file == null || !file.exists()) {
            throw new IOException("Arquivo inválido ou não encontrado para upload.");
        }
        if (cardID == null || cardID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID do Cartão não pode ser nulo ou vazio.");
        }
        if (commentText == null || commentText.trim().isEmpty()) {
            throw new IllegalArgumentException("Texto do comentário não pode ser nulo ou vazio.");
        }

        // 1. Upload do arquivo para o cartão
        String attachmentsUrl = TRELLO_API_URL + "/cards/" + cardID + "/attachments" + buildAuthQueryString(true);
        RequestBody fileBody;
        try {
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = "application/octet-stream"; // Fallback genérico
            }
            fileBody = RequestBody.create(file, MediaType.parse(contentType));
        } catch (IOException e) {
            System.err.println("Não foi possível determinar o tipo de conteúdo do arquivo, usando application/octet-stream: " + e.getMessage());
            fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        }

        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .addFormDataPart("name", file.getName()) // Nome do anexo no Trello
                .build();

        Request uploadRequest = new Request.Builder().url(attachmentsUrl).post(multipartBody).build();
        JSONObject attachmentJson = null;
        try (Response uploadResponse = okHttpClient.newCall(uploadRequest).execute()) {
            String responseBodyString = uploadResponse.body() != null ? uploadResponse.body().string() : "";
            if (!uploadResponse.isSuccessful()) {
                throw new TrelloApiException("Falha ao enviar arquivo para o Trello: " + responseBodyString, uploadResponse.code());
            }
            attachmentJson = new JSONObject(responseBodyString); // Guarda info do anexo se precisar
            System.out.println("Arquivo enviado com sucesso: " + attachmentJson.optString("name", file.getName()));
        } catch (JSONException e) {
            throw new IOException("Erro ao parsear resposta do upload do arquivo: " + e.getMessage(), e);
        }


        // 2. Postar o comentário no cartão
        String commentsUrl = TRELLO_API_URL + "/cards/" + cardID + "/actions/comments" + buildAuthQueryString(true);
        String encodedCommentText;
        try {
            encodedCommentText = URLEncoder.encode(commentText, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            // Esta exceção é improvável com StandardCharsets.UTF_8
            throw new RuntimeException("Erro ao codificar texto do comentário com UTF-8", e);
        }

        // O texto do comentário é passado como parâmetro de query 'text' para este endpoint
        String finalCommentUrl = commentsUrl + "&text=" + encodedCommentText;

        HttpRequest commentHttpRequest = HttpRequest.newBuilder()
                .uri(URI.create(finalCommentUrl))
                .POST(HttpRequest.BodyPublishers.noBody()) // O corpo está vazio, texto vai na URL
                .header("Accept", "application/json")
                .build();

        try {
            HttpResponse<String> commentHttpResponse = httpClient.send(commentHttpRequest, HttpResponse.BodyHandlers.ofString());
            if (commentHttpResponse.statusCode() < 200 || commentHttpResponse.statusCode() >= 300) {
                // Se o comentário falhar, idealmente deveríamos tentar deletar o anexo recém-criado
                if (attachmentJson != null && attachmentJson.has("id")) {
                    System.err.println("Comentário falhou após upload do anexo. Tentando deletar anexo: " + attachmentJson.getString("id"));
                    try {
                        deleteAttachment(cardID, attachmentJson.getString("id"));
                    } catch (Exception eDelete) {
                        System.err.println("Falha ao tentar deletar anexo após falha no comentário: " + eDelete.getMessage());
                    }
                }
                throw new TrelloApiException("Falha ao postar comentário no Trello: " + commentHttpResponse.body(), commentHttpResponse.statusCode());
            }
            System.out.println("Comentário postado com sucesso no cartão: " + cardID);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (attachmentJson != null && attachmentJson.has("id")) {
                System.err.println("Comentário interrompido após upload do anexo. Tentando deletar anexo: " + attachmentJson.getString("id"));
                try {
                    deleteAttachment(cardID, attachmentJson.getString("id"));
                } catch (Exception eDelete) {
                    System.err.println("Falha ao tentar deletar anexo após interrupção no comentário: " + eDelete.getMessage());
                }
            }
            throw new IOException("Operação de postar comentário interrompida.", e);
        } catch (JSONException e) {
            throw new TrelloApiException("Erro ao processar JSON durante a postagem de comentário: " + e.getMessage(), -1, e);
        }
    }


    /**
     * Deleta uma ação de comentário.
     *
     * @param commentActionID ID da ação do comentário.
     * @throws TrelloApiException Se ocorrer um erro na API.
     * @throws IOException Se ocorrer um erro de I/O.
     * @throws InterruptedException Se a operação for interrompida.
     */
    public void deleteCommentAction(String commentActionID) throws TrelloApiException, IOException, InterruptedException {
        if (commentActionID == null || commentActionID.trim().isEmpty()) {
            throw new IllegalArgumentException("ID da Ação do Comentário não pode ser nulo ou vazio.");
        }
        String endpoint = "/actions/" + commentActionID + buildAuthQueryString(true);
        HttpResponse<String> response = makeApiRequest("DELETE", endpoint, null);

        if (response.statusCode() < 200 || response.statusCode() >= 300) { // 204 No Content também é sucesso para DELETE
            throw new TrelloApiException("Falha ao deletar comentário: " + response.body(), response.statusCode());
        }
    }

    /**
     * Obtém o ID de uma ação (como um comentário) de um JSONObject.
     *
     * @param actionJson JSONObject da ação.
     * @return O ID da ação.
     * @throws JSONException Se o campo 'id' não for encontrado ou não for uma string.
     */
    private String getActionID(JSONObject actionJson) throws JSONException {
        if (actionJson == null ) {
            throw new IllegalArgumentException("JSON da ação não pode ser nulo.");
        }
        // Lança JSONException se 'id' não existir ou não for string, o que é apropriado.
        return actionJson.getString("id");
    }

    /**
     * Obtém o caminho base do projeto IntelliJ.
     *
     * @return Caminho do projeto ou null se não puder ser determinado.
     */
    private String getProjectPath() {
        if (this.project == null) {
            // System.err.println("Objeto Project do IntelliJ não foi inicializado. Usando diretório temporário padrão.");
            return System.getProperty("java.io.tmpdir"); // Retorna o diretório temporário do sistema como fallback
        }
        VirtualFile projectDir = ProjectUtil.guessProjectDir(this.project);
        if (projectDir != null) {
            return projectDir.getPath();
        }
        String basePath = this.project.getBasePath();
        if (basePath != null) {
            return basePath;
        }
        System.err.println("Não foi possível determinar o caminho do projeto IntelliJ. Usando diretório temporário padrão.");
        return System.getProperty("java.io.tmpdir");
    }

    // --- Métodos Main para Teste (Exemplo) ---
    public static void main(String[] args) {
        // ATENÇÃO: Substitua pelas suas credenciais e IDs reais para testar.
        // É altamente recomendável NÃO colocar credenciais diretamente no código em produção.
        // Use variáveis de ambiente, arquivos de configuração seguros, etc.
        String apiKeyEnv = System.getenv("TRELLO_API_KEY");
        String serverTokenEnv = System.getenv("TRELLO_SERVER_TOKEN");
        String boardIdEnv = System.getenv("TRELLO_BOARD_ID_TEST"); // Use uma variável de ambiente para o board ID de teste
        String cardIdForTestEnv = System.getenv("TRELLO_CARD_ID_TEST"); // Use uma variável de ambiente para o card ID de teste


        if (apiKeyEnv == null || serverTokenEnv == null || apiKeyEnv.isEmpty() || serverTokenEnv.isEmpty()) {
            System.err.println("Chave de API ou Token do Trello não configurados como variáveis de ambiente (TRELLO_API_KEY, TRELLO_SERVER_TOKEN). Testes não podem ser executados.");
            return;
        }
        if (boardIdEnv == null || boardIdEnv.isEmpty()) {
            System.err.println("ID do Quadro de Teste do Trello não configurado como variável de ambiente (TRELLO_BOARD_ID_TEST). Testes não podem ser executados.");
            return;
        }

        Project mockProject = null;
        TrelloService trelloService = new TrelloService(apiKeyEnv, serverTokenEnv, boardIdEnv, mockProject);

        try {
            System.out.println("--- Testando TrelloService ---");

            System.out.println("\n1. Verificando status do quadro...");
            int boardStatus = trelloService.checkBoardStatus();
            System.out.println("Status do Quadro: " + boardStatus + (boardStatus == 200 ? " (OK)" : " (Erro: " + boardStatus + ")"));

            if (boardStatus != 200) {
                System.err.println("Não foi possível conectar ao quadro. Verifique o ID do quadro e as credenciais. Abortando mais testes.");
                return;
            }

            System.out.println("\n2. Obtendo ID do usuário autenticado...");
            String userId = trelloService.getAuthenticatedUserId();
            System.out.println("ID do Usuário Autenticado: " + userId);

            System.out.println("\n3. Obtendo membros do quadro...");
            JSONArray members = trelloService.getBoardMembers();
            System.out.println("Membros do Quadro (" + members.length() + "):");
            for (int i = 0; i < members.length(); i++) {
                System.out.println("  - " + members.getJSONObject(i).optString("fullName", "N/A") + " (ID: " + members.getJSONObject(i).getString("id") + ")");
            }

            System.out.println("\n4. Obtendo cartões do quadro...");
            JSONArray cardsOnBoard = trelloService.getCardsOnBoard();
            System.out.println("Cartões no Quadro (" + cardsOnBoard.length() + "):");
            if (cardsOnBoard.length() > 0) {
                JSONObject firstCard = cardsOnBoard.getJSONObject(0);
                System.out.println("  Exemplo de cartão: " + firstCard.optString("name", "N/A") + " (ID: " + firstCard.getString("id") + ")");
                String listIdOfFirstCard = firstCard.getString("idList");
                System.out.println("  Buscando detalhes da lista do primeiro cartão (ID Lista: " + listIdOfFirstCard +")...");
                JSONObject listDetails = trelloService.getListDetails(listIdOfFirstCard);
                System.out.println("    Detalhes da Lista: " + listDetails.optString("name", "N/A") + " (ID: " + listDetails.getString("id") + ")");
            }


            if (cardIdForTestEnv != null && !cardIdForTestEnv.isEmpty()) {
                System.out.println("\n--- Testes com Cartão Específico (ID: " + cardIdForTestEnv + ") ---");
                File testScenarioFile = new File(System.getProperty("java.io.tmpdir"), "test_scenario_" + System.currentTimeMillis() + ".csv");
                try (FileOutputStream fos = new FileOutputStream(testScenarioFile)) {
                    fos.write("id,acao,resultado\n1,teste de upload,passou".getBytes(StandardCharsets.UTF_8));
                }
                System.out.println("Arquivo de cenário de teste criado: " + testScenarioFile.getAbsolutePath());

                System.out.println("Salvando cenários no cartão " + cardIdForTestEnv + "...");
                trelloService.saveTaitiScenarios(testScenarioFile, cardIdForTestEnv);
                System.out.println("Cenários salvos com sucesso (verifique o cartão no Trello).");

                Thread.sleep(3000); // Pausa para Trello processar e para verificação manual

                System.out.println("Baixando arquivo TAITI do cartão " + cardIdForTestEnv + "...");
                File downloadedFile = trelloService.downloadTaitiFileFromCard(cardIdForTestEnv);
                if (downloadedFile != null && downloadedFile.exists()) {
                    System.out.println("Arquivo baixado com sucesso para: " + downloadedFile.getAbsolutePath() + " (Tamanho: " + downloadedFile.length() + " bytes)");
                    System.out.println("Conteúdo do arquivo baixado: \n" + new String(Files.readAllBytes(downloadedFile.toPath())));
                    downloadedFile.delete();
                } else {
                    System.out.println("Não foi possível baixar o arquivo TAITI ou não foi encontrado.");
                }

                System.out.println("Deletando cenários TAITI do cartão " + cardIdForTestEnv + "...");
                trelloService.deleteTaitiScenarios(cardIdForTestEnv); // Testa a deleção
                System.out.println("Cenários TAITI (comentário e anexo .csv) deletados do cartão " + cardIdForTestEnv + " (verifique o cartão no Trello).");

                testScenarioFile.delete();
            } else {
                System.out.println("\nAVISO: TRELLO_CARD_ID_TEST não configurado. Pulando testes de save/download/delete de cenários em cartão específico.");
            }

            System.out.println("\n--- Testes concluídos ---");

        } catch (TrelloApiException e) {
            System.err.println("Erro da API do Trello: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Erro de I/O: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("Operação interrompida: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) { // Captura JSONException e outras
            System.err.println("Erro inesperado durante os testes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
