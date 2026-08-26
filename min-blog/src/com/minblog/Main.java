package com.minblog;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * MinBlog 入口。
 * 用法：java -Dfile.encoding=UTF-8 -cp out com.minblog.Main
 * 端口通过环境变量 PORT 配置，默认 8080。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        int port = defaultPort();
        Path dataFile = Paths.get("data", "posts.json");
        Path webDir = Paths.get("web");

        PostStore store = new PostStore(dataFile);
        store.load();

        BlogServer server = new BlogServer(port, store, webDir, "MinBlog");
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        System.out.println("========================================");
        System.out.println("  MinBlog 已启动");
        System.out.println("  地址: http://localhost:" + server.port());
        System.out.println("  数据: " + dataFile.toAbsolutePath().normalize());
        System.out.println("  按 Ctrl+C 停止");
        System.out.println("========================================");
    }

    private static int defaultPort() {
        String raw = System.getenv("PORT");
        if (raw != null && !raw.isBlank()) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // 非法 PORT 使用默认值
            }
        }
        return 8080;
    }
}
