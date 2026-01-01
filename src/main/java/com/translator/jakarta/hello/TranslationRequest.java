package com.translator.jakarta.hello;

public class TranslationRequest {
    private String text;

    // "arabic" (par défaut) ou "latin"
    private String script;

    // Optionnel: contrôle de la créativité (ex: 0.2)
    private Double temperature;

    public TranslationRequest() {}

    public TranslationRequest(String text, String script, Double temperature) {
        this.text = text;
        this.script = script;
        this.temperature = temperature;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
}
