package org.auto.comp1945_project.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class BiomeWebServer {

    private static HttpServer server;
    private static final int PORT = 25580;

    public static void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", BiomeWebServer::handleStatic);
            server.createContext("/data", BiomeWebServer::handleData);
            server.createContext("/history", BiomeWebServer::handleHistory);  // 🔥 新增
            server.setExecutor(Executors.newFixedThreadPool(2));
            server.start();
            System.out.println("[BiomeWebServer] ✅ http://localhost:" + PORT);
        } catch (IOException e) {
            System.err.println("[BiomeWebServer] ❌ 啟動失敗: " + e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ==================== 靜態檔案 ====================

    private static void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        String resourcePath = "/web" + path;
        InputStream is = BiomeWebServer.class.getResourceAsStream(resourcePath);

        if (is == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        byte[] bytes = is.readAllBytes();
        is.close();

        String contentType;
        if (path.endsWith(".html")) contentType = "text/html; charset=utf-8";
        else if (path.endsWith(".css")) contentType = "text/css; charset=utf-8";
        else if (path.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
        else contentType = "application/octet-stream";

        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== /data ====================

    private static void handleData(HttpExchange exchange) throws IOException {
        Map<String, Double> probs = BiomeGuessHUD.getPredictionsSnapshot();
        String coord = BiomeGuessHUD.lastCoord;
        String block = BiomeGuessHUD.lastBrokenBlock;
        long tick = BiomeGuessHUD.lastTick;
        String biome = BiomeGuessHUD.lastBiome;

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"probs\":{");
        boolean first = true;
        for (Map.Entry<String, Double> e : probs.entrySet()) {
            if (!first) json.append(",");
            double v = e.getValue();
            if (Double.isNaN(v) || Double.isInfinite(v)) v = 0.0;
            json.append("\"").append(e.getKey()).append("\":").append(v);
            first = false;
        }
        json.append("},");
        json.append("\"coord\":\"").append(escape(coord)).append("\",");
        json.append("\"block\":\"").append(escape(block)).append("\",");
        json.append("\"tick\":").append(tick).append(",");
        json.append("\"biome\":\"").append(escape(biome)).append("\"");
        json.append("}");

        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== /history ====================

    private static void handleHistory(HttpExchange exchange) throws IOException {
        List<BiomeHistory.HistoryEntry> history = BiomeHistory.getHistory();

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) json.append(",");
            BiomeHistory.HistoryEntry e = history.get(i);
            json.append("{\"tick\":").append(e.tick)
                    .append(",\"red\":").append(safe(e.red))
                    .append(",\"blue\":").append(safe(e.blue))
                    .append(",\"yellow\":").append(safe(e.yellow))
                    .append("}");
        }
        json.append("]");

        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static double safe(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return v;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}