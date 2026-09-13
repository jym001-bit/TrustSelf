package com.paicli.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MainConfigBootstrapTest {

    @Test
    void createsDefaultChromeDevtoolsMcpConfigWhenMissing(@TempDir Path tempHome) throws Exception {
        Main.McpConfigBootstrapResult result = Main.ensureDefaultMcpConfig(tempHome);

        Path config = tempHome.resolve(".paicli").resolve("mcp.json");
        assertTrue(result.created());
        assertTrue(Files.exists(config));
        String content = Files.readString(config);
        assertTrue(content.contains("\"chrome-devtools\""));
        assertTrue(content.contains("chrome-devtools-mcp@latest"));
        assertTrue(content.contains("--isolated=true"));
    }

    @Test
    void doesNotOverwriteExistingUserConfig(@TempDir Path tempHome) throws Exception {
        Path config = tempHome.resolve(".paicli").resolve("mcp.json");
        Files.createDirectories(config.getParent());
        String original = """
                {
                  "mcpServers": {
                    "filesystem": {
                      "command": "npx",
                      "args": ["-y", "@modelcontextprotocol/server-filesystem"]
                    }
                  }
                }
                """;
        Files.writeString(config, original);

        Main.McpConfigBootstrapResult result = Main.ensureDefaultMcpConfig(tempHome);

        assertFalse(result.created());
        assertEquals(original, Files.readString(config));
        assertTrue(result.message().contains("未配置 chrome-devtools"));
    }

    // ---- EMBEDDING_* 从 .env 提升为系统属性（EmbeddingClient 不读 .env，靠这里兜底）----

    private final Map<String, String> savedEmbeddingProperties = new HashMap<>();

    @BeforeEach
    void isolateEmbeddingConfig() {
        for (String key : Main.EMBEDDING_CONFIG_KEYS) {
            savedEmbeddingProperties.put(key, System.getProperty(key));
            System.clearProperty(key);
        }
        for (String key : Main.EMBEDDING_CONFIG_KEYS) {
            String ambient = System.getenv(key);
            Assumptions.assumeTrue(ambient == null || ambient.isBlank(),
                    "本机已设置环境变量 " + key + "，该键的 .env 提升用例不成立");
        }
    }

    @AfterEach
    void restoreEmbeddingConfig() {
        for (Map.Entry<String, String> entry : savedEmbeddingProperties.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    @Test
    void propagatesEmbeddingConfigFromDotEnv(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                # Embedding 配置
                EMBEDDING_PROVIDER=openai
                EMBEDDING_MODEL=text-embedding-v4
                EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
                EMBEDDING_API_KEY=sk-test-key
                """);

        Main.propagateEmbeddingConfig(envFile, tempDir.resolve("home").resolve(".env"));

        assertEquals("openai", System.getProperty("EMBEDDING_PROVIDER"));
        assertEquals("text-embedding-v4", System.getProperty("EMBEDDING_MODEL"));
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", System.getProperty("EMBEDDING_BASE_URL"));
        assertEquals("sk-test-key", System.getProperty("EMBEDDING_API_KEY"));
    }

    @Test
    void keepsExplicitSystemPropertyOverDotEnv(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "EMBEDDING_MODEL=from-dot-env\n");
        System.setProperty("EMBEDDING_MODEL", "from-d-flag");

        Main.propagateEmbeddingConfig(envFile, tempDir.resolve("home").resolve(".env"));

        assertEquals("from-d-flag", System.getProperty("EMBEDDING_MODEL"));
    }

    @Test
    void toleratesMissingDotEnvFiles(@TempDir Path tempDir) {
        Main.propagateEmbeddingConfig(
                tempDir.resolve(".env"),
                tempDir.resolve("home").resolve(".env"));

        for (String key : Main.EMBEDDING_CONFIG_KEYS) {
            assertNull(System.getProperty(key));
        }
    }

    @Test
    void propagatesOnlyKeysPresentInDotEnv(@TempDir Path tempDir) throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "EMBEDDING_PROVIDER=zhipu\n\n# EMBEDDING_MODEL=commented-out\n");

        Main.propagateEmbeddingConfig(envFile, tempDir.resolve("home").resolve(".env"));

        assertEquals("zhipu", System.getProperty("EMBEDDING_PROVIDER"));
        assertNull(System.getProperty("EMBEDDING_MODEL"));
        assertNull(System.getProperty("EMBEDDING_API_KEY"));
    }
}
