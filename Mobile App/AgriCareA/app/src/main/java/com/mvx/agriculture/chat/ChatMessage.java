package com.mvx.agriculture.chat;

/** One line of the conversation, as shown in the transcript and sent to the model. */
public class ChatMessage {

    public enum Role { USER, ASSISTANT }

    private final Role role;
    private String text;

    public ChatMessage(Role role, String text) {
        this.role = role;
        this.text = text;
    }

    public Role role() {
        return role;
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /** The wire value the OpenAI-compatible API expects. */
    public String apiRole() {
        return role == Role.USER ? "user" : "assistant";
    }
}
