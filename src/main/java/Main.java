import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        List<String> validPaths = List.of(
                "/index.html", "/spring.svg", "/spring.png",
                "/resources.html", "/styles.css", "/app.js",
                "/links.html", "/forms.html", "/classic.html",
                "/events.html", "/events.js"
        );

        Server server = new Server(9999, validPaths);

        server.addHandler("GET", "/messages", (request, out) -> {
            // демонстрация работы с query-параметрами
            String last = request.getQueryParam("last");
            Map<String, List<String>> allParams = request.getQueryParams();

            StringBuilder body = new StringBuilder();
            body.append("Hello from GET /messages\r\n");
            body.append("Path: ").append(request.getPath()).append("\r\n");

            if (allParams.isEmpty()) {
                body.append("No query parameters.\r\n");
            } else {
                body.append("Query parameters:\r\n");
                for (Map.Entry<String, List<String>> entry : allParams.entrySet()) {
                    body.append("  ").append(entry.getKey())
                        .append(" = ").append(entry.getValue()).append("\r\n");
                }
            }

            if (last != null) {
                body.append("\r\nRequested last ").append(last).append(" messages.\r\n");
            }

            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "
                    + body.toString().getBytes(StandardCharsets.UTF_8).length
                    + "\r\nConnection: close\r\n\r\n" + body;
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        });

        server.addHandler("POST", "/messages", (request, out) -> {
            StringBuilder requestBody = new StringBuilder();
            if (request.getBody() != null) {
                byte[] buf = new byte[1024];
                int read;
                while ((read = request.getBody().read(buf)) != -1) {
                    requestBody.append(new String(buf, 0, read, StandardCharsets.UTF_8));
                }
            }
            String body = "Received POST: " + requestBody;
            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "
                    + body.length() + "\r\nConnection: close\r\n\r\n" + body;
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        });

        server.start();
    }
}
