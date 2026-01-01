package com.translator.jakarta.hello;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/translate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TranslatorResource {

    // Ollama tourne localement sur le port 11434
    private static final String OLLAMA_CHAT_URL = "http://localhost:11434/api/chat";

    // Modèle par défaut (Cloud). IMPORTANT: il faut être connecté (ollama signin)
    // et avoir une version Ollama compatible avec les modèles cloud.
    private static final String DEFAULT_MODEL = "deepseek-v3.1:671b-cloud";

    // Température par défaut : faible = traduction plus stable et fidèle
    private static final double DEFAULT_TEMPERATURE = 0.2;

    // Client HTTP (Java 11+)
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @POST
    public Response translate(TranslationRequest request) {
        try {
            // Validation du body JSON
            if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse("Le champ 'text' est obligatoire."))
                        .build();
            }

            String input = request.getText().trim();

            // "arabic" (par défaut) ou "latin"
            String script = normalizeScript(request.getScript());

            // Forcer le modèle cloud (ignore request.getModel())
            String model = DEFAULT_MODEL;

            // Température : celle du body sinon valeur par défaut
            double temperature = (request.getTemperature() != null)
                    ? request.getTemperature()
                    : DEFAULT_TEMPERATURE;

            String translated = callOllamaChat(input, script, model, temperature);

            return Response.ok(new TranslationResponse(translated)).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Translation failed: " + e.getMessage()))
                    .build();
        }
    }

    // Normalise la valeur script pour éviter les valeurs invalides
    private String normalizeScript(String script) {
        if (script == null) return "arabic";
        String s = script.trim().toLowerCase();
        if (s.equals("latin") || s.equals("latn")) return "latin";
        return "arabic";
    }

    private String callOllamaChat(String sourceText, String script, String model, double temperature) throws Exception {

     
    	// Darija en arabe ou en latin
    	String scriptRule;
    	if ("latin".equalsIgnoreCase(script)) {
    	    scriptRule = "Write the Darija using LATIN letters (Darija latin).";
    	} else {
    	    scriptRule = "Write the Darija using ARABIC script (الحروف العربية).";
    	}

        // System prompt : sortie stricte (uniquement la traduction)
        String system =
                "You are a professional translator. "
              + "Your job: translate the user text into Moroccan Arabic Darija. "
              + scriptRule + " "
              + "Output ONLY the translation, no quotes, no explanations, no extra text.";

        // User prompt : détection automatique de la langue source
        String user =
                "Detect the source language automatically and translate this text to Moroccan Arabic Darija:\n\n"
              + sourceText;

        // Corps JSON attendu par /api/chat
        JsonArrayBuilder messages = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("role", "system")
                        .add("content", system))
                .add(Json.createObjectBuilder()
                        .add("role", "user")
                        .add("content", user));

        JsonObjectBuilder bodyBuilder = Json.createObjectBuilder()
                .add("model", model)
                .add("messages", messages)
                .add("stream", false)
                .add("options", Json.createObjectBuilder()
                        .add("temperature", temperature));

        String requestBody = bodyBuilder.build().toString();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_CHAT_URL))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> httpResponse = client.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (httpResponse.statusCode() != 200) {
            throw new RuntimeException("Ollama API status=" + httpResponse.statusCode()
                    + " body=" + httpResponse.body());
        }

        // Réponse attendue :
        // { ..., "message": { "role": "assistant", "content": "..." }, "done": true, ... }
        String responseBody = httpResponse.body();
        try (JsonReader reader = Json.createReader(new StringReader(responseBody))) {
            JsonObject root = reader.readObject();

            if (!root.containsKey("message")) {
                throw new RuntimeException("Réponse Ollama invalide (champ 'message' absent) : " + responseBody);
            }

            JsonObject message = root.getJsonObject("message");
            String content = message.getString("content", "").trim();

            if (content.isEmpty()) {
                throw new RuntimeException("Traduction vide renvoyée par Ollama. Réponse complète : " + responseBody);
            }

            return content;
        }
    }
}
