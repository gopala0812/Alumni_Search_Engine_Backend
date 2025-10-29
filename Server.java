package backend;

import java.io.*;
import java.net.*;
import java.util.*;
import com.google.gson.*;
import com.sun.net.httpserver.*;

public class Server {

    // Alumni data model
    static class Alumni {
        int id;
        String name;
        String department;
        int year;
        String email;
        String phone;
        String address;
        String job;
        String company;
        double cgpa;
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        System.out.println("✅ Backend Server running on port " + port);

        // Root (for testing)
        server.createContext("/", (exchange -> {
            handleCORS(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = "Alumni Search Engine Backend Active 🚀";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            }
        }));

        // -------------------- SEARCH --------------------
        server.createContext("/search", (exchange -> {
            handleCORS(exchange);
            if (isPreflight(exchange)) return;

            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String name = params.getOrDefault("name", "").toLowerCase();
                String dept = params.getOrDefault("department", "").toLowerCase();
                String year = params.getOrDefault("year", "");

                Alumni[] alumniList = readDatabase();
                List<Alumni> result = new ArrayList<>();

                for (Alumni a : alumniList) {
                    boolean match = true;
                    if (!name.isEmpty() && !a.name.toLowerCase().contains(name)) match = false;
                    if (!dept.isEmpty() && !a.department.toLowerCase().equals(dept)) match = false;
                    if (!year.isEmpty() && a.year != Integer.parseInt(year)) match = false;
                    if (match) result.add(a);
                }

                sendJson(exchange, result);
            }
        }));

        // -------------------- STATS --------------------
        server.createContext("/stats", (exchange -> {
            handleCORS(exchange);
            if (isPreflight(exchange)) return;

            if ("GET".equals(exchange.getRequestMethod())) {
                Alumni[] alumniList = readDatabase();
                Map<String, Object> stats = new HashMap<>();
                stats.put("total_alumni", alumniList.length);

                int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                int recentBatch = 0, currentBatch = 0;
                Map<String, Integer> deptCounts = new HashMap<>();

                for (Alumni a : alumniList) {
                    if (a.year == currentYear - 1) recentBatch++;
                    if (a.year == currentYear) currentBatch++;
                    deptCounts.put(a.department, deptCounts.getOrDefault(a.department, 0) + 1);
                }

                stats.put("recent_batch", recentBatch);
                stats.put("current_batch", currentBatch);
                stats.put("departments", deptCounts.keySet().size());
                stats.put("dept_counts", deptCounts);

                sendJson(exchange, stats);
            }
        }));

        // -------------------- ADD BULK --------------------
        server.createContext("/add-bulk", (exchange -> {
            handleCORS(exchange);
            if (isPreflight(exchange)) return;

            if ("POST".equals(exchange.getRequestMethod())) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"))) {
                    Gson gson = new Gson();
                    Alumni[] newAlumniArray = gson.fromJson(reader, Alumni[].class);

                    Alumni[] alumniList = readDatabase();
                    List<Alumni> list = new ArrayList<>(Arrays.asList(alumniList));
                    int nextId = list.size() + 1;

                    for (Alumni a : newAlumniArray) {
                        a.id = nextId++;
                        list.add(a);
                    }

                    writeDatabase(list.toArray(new Alumni[0]));
                    sendJson(exchange, Collections.singletonMap("status", "success"));
                } catch (Exception e) {
                    e.printStackTrace();
                    sendJson(exchange, Collections.singletonMap("status", "error"));
                }
            }
        }));

        // -------------------- DOWNLOAD --------------------
        server.createContext("/download", (exchange -> {
            handleCORS(exchange);
            if (isPreflight(exchange)) return;

            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, String> params = queryToMap(exchange.getRequestURI().getQuery());
                String idParam = params.get("id");
                String batchParam = params.get("batch");

                Alumni[] alumniList = readDatabase();
                List<Alumni> result = new ArrayList<>();

                if (idParam != null) {
                    int id = Integer.parseInt(idParam);
                    for (Alumni a : alumniList) if (a.id == id) result.add(a);
                } else if (batchParam != null) {
                    int batch = Integer.parseInt(batchParam);
                    for (Alumni a : alumniList) if (a.year == batch) result.add(a);
                }

                sendJson(exchange, result);
            }
        }));

        server.start();
    }

    // -------------------- HELPERS --------------------

    private static boolean isPreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleCORS(exchange);
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    // ✅ FIXED CORS (for Render + local Live Server + future GitHub Pages)
    private static void handleCORS(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        String origin = exchange.getRequestHeaders().getFirst("Origin");

        if (origin != null && (
            origin.equals("http://127.0.0.1:5500") ||
            origin.equals("http://localhost:5500") ||
            origin.equals("https://gopala0812.github.io")
        )) {
            headers.set("Access-Control-Allow-Origin", origin);
        } else {
            headers.set("Access-Control-Allow-Origin", "*");
        }

        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        headers.set("Access-Control-Allow-Credentials", "true");
    }

    private static Alumni[] readDatabase() throws IOException {
        File file = new File("Database.json");
        if (!file.exists()) return new Alumni[0];

        try (Reader reader = new FileReader(file)) {
            Gson gson = new Gson();
            Alumni[] alumniList = gson.fromJson(reader, Alumni[].class);
            return alumniList != null ? alumniList : new Alumni[0];
        }
    }

    private static void writeDatabase(Alumni[] alumniList) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = new FileWriter("Database.json")) {
            gson.toJson(alumniList, writer);
        }
    }

    private static void sendJson(HttpExchange exchange, Object obj) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String response = gson.toJson(obj);
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) result.put(entry[0], entry[1]);
            else result.put(entry[0], "");
        }
        return result;
    }
}

