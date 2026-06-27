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
            String body = "Hello from GET /messages";
            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "
                    + body.length() + "\r\nConnection: close\r\n\r\n" + body;
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        });

        server.addHandler("POST", "/messages", (request, out) -> {
            // демонстрация getPostParam и getPostParams
            String login = request.getPostParam("login");
            String password = request.getPostParam("password");
            Map<String, List<String>> allParams = request.getPostParams();

            StringBuilder body = new StringBuilder();
            body.append("Received POST /messages\r\n");

            if (allParams.isEmpty()) {
                // fallback: читаем тело как сырой текст
                StringBuilder rawBody = new StringBuilder();
                if (request.getBody() != null) {
                    byte[] buf = new byte[1024];
                    int read;
                    while ((read = request.getBody().read(buf)) != -1) {
                        rawBody.append(new String(buf, 0, read, StandardCharsets.UTF_8));
                    }
                }
                body.append("Raw body: ").append(rawBody).append("\r\n");
            } else {
                body.append("Parsed form params:\r\n");
                for (Map.Entry<String, List<String>> entry : allParams.entrySet()) {
                    body.append("  ").append(entry.getKey())
                        .append(" = ").append(entry.getValue()).append("\r\n");
                }
            }

            if (login != null && password != null) {
                body.append("\r\nLogin attempt: ").append(login)
                    .append(" / ").append("*".repeat(password.length())).append("\r\n");
            }

            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "
                    + body.toString().getBytes(StandardCharsets.UTF_8).length
                    + "\r\nConnection: close\r\n\r\n" + body;
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        });

        server.start();
    }
}
