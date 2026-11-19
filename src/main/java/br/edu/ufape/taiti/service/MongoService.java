package br.edu.ufape.taiti.service;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;

import org.bson.Document;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.mongodb.client.model.Filters.eq;

public class MongoService implements Closeable {

    private static final String DATABASE_NAME   = "taiti";
    private static final String COLLECTION_NAME = "scenarios";

    private final MongoClient                  mongoClient;
    private final MongoCollection<Document>    collection;

    public MongoService(String connectionString) {
        if (connectionString == null || connectionString.trim().isEmpty())
            throw new IllegalArgumentException("Connection string do MongoDB não pode ser nula ou vazia.");

        this.mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
        this.collection = database.getCollection(COLLECTION_NAME);
    }

    // ------------------------------------------------------------------
    // Escrita
    // ------------------------------------------------------------------

    /**
     * Salva (ou substitui) o cenário CSV de um cartão no MongoDB.
     *
     * @param file   Arquivo CSV a persistir.
     * @param cardId ID do cartão Trello.
     * @throws IOException      Se o arquivo for inválido ou a leitura falhar.
     * @throws MongoException   Se a operação no banco falhar.
     */
    public void saveScenario(File file, String cardId) throws IOException {
        if (file == null || !file.exists())
            throw new IOException("Arquivo inválido ou não encontrado: "
                    + (file != null ? file.getAbsolutePath() : "null"));
        if (cardId == null || cardId.trim().isEmpty())
            throw new IllegalArgumentException("cardId não pode ser nulo ou vazio.");

        String csvContent = Files.readString(file.toPath());

        Document document = new Document()
                .append("cardId",    cardId)
                .append("fileName",  file.getName())
                .append("content",   csvContent)
                .append("updatedAt", System.currentTimeMillis());

        collection.replaceOne(
                eq("cardId", cardId),
                document,
                new ReplaceOptions().upsert(true)
        );

        System.out.println("Cenário salvo no MongoDB para o cartão: " + cardId);
    }

    /**
     * Remove o cenário de um cartão do MongoDB.
     * Não lança exceção se o documento não existir (operação idempotente).
     *
     * @param cardId ID do cartão Trello.
     * @throws IllegalArgumentException Se cardId for nulo/vazio.
     * @throws MongoException           Se a operação no banco falhar.
     */
    public void deleteScenario(String cardId) {
        if (cardId == null || cardId.trim().isEmpty())
            throw new IllegalArgumentException("cardId não pode ser nulo ou vazio.");

        DeleteResult result = collection.deleteOne(eq("cardId", cardId));

        if (result.getDeletedCount() > 0)
            System.out.println("Cenário removido do MongoDB para o cartão: " + cardId);
        else
            System.out.println("Nenhum cenário encontrado para remover (cartão: " + cardId + ").");
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    /**
     * Verifica se existe um cenário salvo para o cartão informado.
     *
     * @param cardId ID do cartão Trello.
     * @return {@code true} se o documento existir, {@code false} caso contrário.
     * @throws MongoException Se a consulta ao banco falhar.
     */
    public boolean scenarioExists(String cardId) {
        if (cardId == null || cardId.trim().isEmpty())
            throw new IllegalArgumentException("cardId não pode ser nulo ou vazio.");

        return collection.find(eq("cardId", cardId)).first() != null;
    }

    /**
     * Baixa o conteúdo do cenário do MongoDB e grava em arquivo local.
     *
     * @param cardId   ID do cartão Trello.
     * @param basePath Diretório base onde o arquivo será criado
     *                 (usa {@code java.io.tmpdir} se nulo ou vazio).
     * @return Arquivo gravado, ou {@code null} se não houver cenário para o cartão.
     * @throws IOException    Se a gravação do arquivo falhar.
     * @throws MongoException Se a consulta ao banco falhar.
     */
    public File downloadScenario(String cardId, String basePath) throws IOException {
        if (cardId == null || cardId.trim().isEmpty())
            throw new IllegalArgumentException("cardId não pode ser nulo ou vazio.");

        Document document = collection.find(eq("cardId", cardId)).first();
        if (document == null) return null;

        String content = document.getString("content");
        if (content == null || content.isEmpty()) return null;

        if (basePath == null || basePath.trim().isEmpty())
            basePath = System.getProperty("java.io.tmpdir");

        Path tempDir = Paths.get(basePath, "temp_taiti");
        Files.createDirectories(tempDir);

        File downloadedFile = tempDir.resolve("taiti_" + cardId + ".csv").toFile();
        Files.writeString(downloadedFile.toPath(), content);

        System.out.println("Cenário baixado para: " + downloadedFile.getAbsolutePath());
        return downloadedFile;
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    /**
     * Fecha a conexão com o MongoDB.
     * Chame este método quando o {@code TrelloService} (ou o plugin) for descartado.
     */
    @Override
    public void close() {
        mongoClient.close();
        System.out.println("Conexão MongoDB encerrada.");
    }
}
