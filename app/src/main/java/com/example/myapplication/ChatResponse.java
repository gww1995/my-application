package com.example.myapplication;

import java.util.List;

/**
 * 响应体模型
 */
public class ChatResponse {

    private List<Choice> choices;

    public List<Choice> getChoices(){
        return choices;
    }

    public void setChoices(List<Choice> choices){
        this.choices = choices;
    }

    public static class Choice{
        private Message message;

        public Choice(Message message) {
            this.message = message;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }
    }

    public static class Message{
        private String content;
        private String role;
        private String reasoning_content;

        public Message(String content, String role, String reasoning_content) {
            this.content = content;
            this.role = role;
            this.reasoning_content = reasoning_content;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getReasoning_content() {
            return reasoning_content;
        }

        public void setReasoning_content(String reasoning_content) {
            this.reasoning_content = reasoning_content;
        }
    }
}
