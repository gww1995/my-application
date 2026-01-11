package com.example.myapplication;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求实体类
 */
public class ChatRequest {

    private String model;
    private ArrayList<Messages> messages;

    public ChatRequest(String model, List<Messages> messages) {
        this.model = model;
        this.messages = new ArrayList<>(messages);
    }

    public void setInput(ArrayList<Messages> messages) {
        this.messages = messages;
    }

    public ArrayList<Messages> getInput() {
        return messages;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getModel() {
        return model;
    }


    public static class Messages {
        private String role;
        private List<Content> content;

        @Override
        public String toString() {
            return "Messages{" +
                    "role='" + role + '\'' +
                    ", content=" + content +
                    '}';
        }

        public Messages(String role, Content content) {
            this.role = role;
            this.content = new ArrayList<>();
            this.content.add(content);
        }
        public List<Content> getContent() {
            return content;
        }

        public void setContent(List<Content> content) {
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
    public static class Content {
        private String type; // 类型：input_image（图片）/ input_text（文本）
        private String text; // 文本内容（仅type=input_text时生效）

        public Content(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        public String getText() {
            return text;
        }
        public void setText(String text) {
            this.text = text;
        }
    }
}
