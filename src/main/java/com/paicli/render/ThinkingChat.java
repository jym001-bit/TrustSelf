package com.paicli.render;

import com.paicli.llm.LlmClient;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/** Routes reasoning to the terminal details store without changing model history. */
public final class ThinkingChat {
    private ThinkingChat() {}

    public static LlmClient.ChatResponse chat(LlmClient client, List<LlmClient.Message> messages,
            List<LlmClient.Tool> tools, LlmClient.StreamListener listener, Consumer<String> sink) throws IOException {
        if (sink == null) return client.chat(messages, tools, listener);
        StringBuilder pending = new StringBuilder();
        boolean[] receivedReasoning = {false};
        Runnable flush = () -> {
            if (!pending.isEmpty()) {
                sink.accept(pending.toString());
                pending.setLength(0);
            }
        };
        try {
            LlmClient.ChatResponse response = client.chat(messages, tools, new LlmClient.StreamListener() {
                public void onReasoningDelta(String delta) {
                    if (delta != null && !delta.isEmpty()) {
                        receivedReasoning[0] = true;
                        pending.append(delta);
                    }
                }
                public void onContentDelta(String delta) {
                    flush.run();
                    listener.onContentDelta(delta);
                }
            });
            if (!receivedReasoning[0] && response.reasoningContent() != null) {
                pending.append(response.reasoningContent());
            }
            return response;
        } finally {
            flush.run();
        }
    }
}
