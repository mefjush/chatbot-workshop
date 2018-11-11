package com.example.chat.connection;

import com.example.chat.ChatBot;
import com.example.chat.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

public class ChatConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatConnection.class);

    private static final String TOPIC = "/topic/public";

    private final WebSocketStompClient stompClient;
    private final ChatBot chatBot;

    private StompSession session;

    public ChatConnection(WebSocketStompClient stompClient, ChatBot chatBot) {
        this.stompClient = stompClient;
        this.chatBot = chatBot;
    }

    public synchronized void connect(String url) {
        StompSessionHandlerAdapter stompHandler = new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessage.class;
            }
        };
        try {
            session = stompClient.connect(url, stompHandler).get(5, TimeUnit.SECONDS);
            sendNotification(ChatMessage.MessageType.JOIN);
            subscribe();
        } catch (Exception e) {
            throw new ChatConnectionException(e);
        }
    }

    private void subscribe() {
        session.subscribe(TOPIC, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessage.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                ChatMessage msg = (ChatMessage) payload;
                if (ChatMessage.MessageType.CHAT.equals(msg.getType())) {
                    LOGGER.info("==> " + msg);
                    String reply = chatBot.onUserMessage(msg);
                    if (reply != null && !reply.equals("")) {
                        sendChat(reply);
                    }
                }
            }
        });
    }

    public synchronized void disconnect() {
        if (session != null) {
            sendNotification(ChatMessage.MessageType.LEAVE);
            session.disconnect();
        }
    }

    private void sendNotification(ChatMessage.MessageType messageType) {
        ChatMessage chatMessage = new ChatMessage(messageType, chatBot.getBotName());
        send(chatMessage);
    }

    private void sendChat(String content) {
        ChatMessage chatMessage = new ChatMessage(content, chatBot.getBotName());
        send(chatMessage);
    }

    private void send(ChatMessage chatMessage) {
        LOGGER.info("<== " + chatMessage);
        session.send(TOPIC, chatMessage);
    }
}
