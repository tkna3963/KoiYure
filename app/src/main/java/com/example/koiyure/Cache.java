package com.example.koiyure;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * type別に日付付きメッセージを保存し、今日以外は自動削除するキャッシュ
 * スレッドセーフなシングルトン実装
 */
public class Cache {

    private static Cache instance;

    public static synchronized Cache getInstance() {
        if (instance == null) {
            instance = new Cache();
        }
        return instance;
    }

    private Cache() {
        // プライベートコンストラクタ
    }

    private static class TimedMessage {
        final String message;
        final LocalDate date;

        TimedMessage(String message) {
            this.message = message;
            this.date = LocalDate.now();
        }

        boolean isToday() {
            return date.equals(LocalDate.now());
        }
    }

    private final Map<String, List<TimedMessage>> cacheByType = new HashMap<>();

    // 以下、既存のメソッドはそのまま
    public synchronized void add(String type, String message) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("typeは空にできません");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("メッセージは空にできません");
        }

        cleanupOldMessages(type);
        cacheByType.computeIfAbsent(type, k -> new ArrayList<>())
                .add(new TimedMessage(message));
    }

    public synchronized List<String> getMessages(String type) {
        if (type == null) return new ArrayList<>();

        cleanupOldMessages(type);
        List<TimedMessage> messages = cacheByType.get(type);
        if (messages == null) return new ArrayList<>();

        return messages.stream()
                .map(tm -> tm.message)
                .collect(Collectors.toList());
    }

    public synchronized List<String> popMessages(String type) {
        List<String> messages = getMessages(type);
        if (type != null) {
            cacheByType.remove(type);
        }
        return messages;
    }

    public synchronized int size(String type) {
        if (type == null) return 0;
        cleanupOldMessages(type);
        List<TimedMessage> messages = cacheByType.get(type);
        return messages == null ? 0 : messages.size();
    }

    public synchronized int totalSize() {
        cleanupAllOldMessages();
        return cacheByType.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public synchronized void clear(String type) {
        if (type != null) {
            cacheByType.remove(type);
        }
    }

    public synchronized void clearAll() {
        cacheByType.clear();
    }

    public synchronized boolean isEmpty(String type) {
        if (type == null) return true;
        cleanupOldMessages(type);
        List<TimedMessage> messages = cacheByType.get(type);
        return messages == null || messages.isEmpty();
    }

    public synchronized List<String> getTypes() {
        cleanupAllOldMessages();
        return new ArrayList<>(cacheByType.keySet());
    }

    private void cleanupOldMessages(String type) {
        List<TimedMessage> messages = cacheByType.get(type);
        if (messages != null) {
            messages.removeIf(tm -> !tm.isToday());
            if (messages.isEmpty()) {
                cacheByType.remove(type);
            }
        }
    }

    public synchronized List<String> getAllMessages() {
        return cacheByType.values().stream()
                .flatMap(List::stream)
                .map(tm -> tm.message)
                .collect(Collectors.toList());
    }

    private void cleanupAllOldMessages() {
        cacheByType.keySet().removeIf(type -> {
            List<TimedMessage> messages = cacheByType.get(type);
            messages.removeIf(tm -> !tm.isToday());
            return messages.isEmpty();
        });
    }
}