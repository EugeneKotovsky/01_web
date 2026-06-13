
import java.nio.charset.StandardCharsets;
import java.util.List;

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
            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
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
            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        });

        server.start();
    }
}