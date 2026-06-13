
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final int port;
    private final List<String> validPaths;
    private final ExecutorService threadPool;

    public Server(int port, List<String> validPaths) {
        this.port = port;
        this.validPaths = validPaths;
        this.threadPool = Executors.newFixedThreadPool(64);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> handleConnection(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private void handleConnection(Socket clientSocket) {
        try (clientSocket;
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())) {

            final String requestLine = in.readLine();
            if (requestLine == null) return;
            final String[] parts = requestLine.split(" ");
            if (parts.length != 3) return;

            final String path = parts[1];
            if (!validPaths.contains(path)) {
                sendNotFound(out);
                return;
            }

            final Path filePath = Path.of("target/classes", path);
            final String mimeType = Files.probeContentType(filePath);

            if (path.equals("/classic.html")) {
                sendClassicPage(filePath, mimeType, out);
                return;
            }

            sendStaticFile(filePath, mimeType, out);

        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private void sendNotFound(BufferedOutputStream out) throws IOException {
        String response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.flush();
    }

    private void sendStaticFile(Path filePath, String mimeType, BufferedOutputStream out) throws IOException {
        long length = Files.size(filePath);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(header.getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    private void sendClassicPage(Path filePath, String mimeType, BufferedOutputStream out) throws IOException {
        String template = Files.readString(filePath);
        byte[] content = template.replace("{time}", LocalDateTime.now().toString()).getBytes();
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + content.length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(header.getBytes());
        out.write(content);
        out.flush();
    }
}