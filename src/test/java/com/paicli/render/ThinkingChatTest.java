package com.paicli.render;

import com.paicli.llm.LlmClient;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ThinkingChatTest {
    @Test
    void routesReasoningAndLateReasoningWithoutChangingResponse() throws Exception {
        var events = new ArrayList<String>();
        var expected = new LlmClient.ChatResponse("assistant", "answer", "firstlate", null, 1, 2, 0);
        LlmClient client = (LlmClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{LlmClient.class}, (proxy, method, args) -> {
                    var listener = (LlmClient.StreamListener) args[2];
                    listener.onReasoningDelta("first");
                    listener.onContentDelta("answer");
                    listener.onReasoningDelta("late");
                    return expected;
                });
        var actual = ThinkingChat.chat(client, List.of(), null, new LlmClient.StreamListener() {
            public void onReasoningDelta(String text) { fail("reasoning must not reach the text renderer"); }
            public void onContentDelta(String text) { events.add("content:" + text); }
        }, text -> events.add("thinking:" + text));
        assertSame(expected, actual);
        assertEquals(List.of("thinking:first", "content:answer", "thinking:late"), events);
    }

    @Test
    void preservesPartialReasoningWhenRequestFails() {
        var thinking = new ArrayList<String>();
        LlmClient client = (LlmClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{LlmClient.class}, (proxy, method, args) -> {
                    ((LlmClient.StreamListener) args[2]).onReasoningDelta("partial");
                    throw new IOException("disconnected");
                });
        assertThrows(IOException.class, () -> ThinkingChat.chat(client, List.of(), null,
                LlmClient.StreamListener.NO_OP, thinking::add));
        assertEquals(List.of("partial"), thinking);
    }
}
