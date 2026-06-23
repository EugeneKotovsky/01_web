
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final int port;
    private final List<String> validPaths;
    private final ExecutorService threadPool;
    private final Map<String, Map<String, Handler>> handlers; // method -> path -> handler

    public Server(int port, List<String> validPaths) {
        this.port = port;
        this.validPaths = validPaths;
        this.threadPool = Executors.newFixedThreadPool(64);
        this.handlers = new ConcurrentHashMap<>();
    }

    public void addHandler(String method, String path, Handler handler) {
        handlers.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
                .put(path, handler);
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

            // request line
            final String requestLine = in.readLine();
            if (requestLine == null) return;
            final String[] parts = requestLine.split(" ");
            if (parts.length != 3) return;

            final String method = parts[0];
            final String path = parts[1];

            // headers
            final Map<String, String> headers = new HashMap<>();
            int contentLength = 0;
            String line;
            while (!(line = in.readLine()).isEmpty()) {
                String[] headerParts = line.split(": ", 2);
                if (headerParts.length == 2) {
                    headers.put(headerParts[0], headerParts[1]);
                    if (headerParts[0].equalsIgnoreCase("Content-Length")) {
                        contentLength = Integer.parseInt(headerParts[1]);
                    }
                }
            }

            // body
            InputStream bodyStream = null;
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int read = in.read(bodyChars, 0, contentLength);
                if (read > 0) {
                    bodyStream = new ByteArrayInputStream(new String(bodyChars, 0, read).getBytes());
                }
            }

            Request request = new Request(method, path, headers, bodyStream);

            Handler handler = getHandler(method, path);
            if (handler != null) {
                handler.handle(request, out);
                return;
            }

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

        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Handler getHandler(String method, String path) {
        Map<String, Handler> methodHandlers = handlers.get(method);
        return methodHandlers != null ? methodHandlers.get(path) : null;
    }

    private void sendNotFound(BufferedOutputStream out) throws IOException {
        String response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        out.write(response.getBytes());
        out.flush();
    }

    private void sendStaticFile(Path filePath, String mimeType, BufferedOutputStream out) throws IOException {
        long length = Files.size(filePath);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: " + mimeType + "\r\nContent-Length: " + length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    private void sendClassicPage(Path filePath, String mimeType, BufferedOutputStream out) throws IOException {
        String template = Files.readString(filePath);
        byte[] content = template.replace("{time}", LocalDateTime.now().toString()).getBytes();
        String header = "HTTP/1.1 200 OK\r\nContent-Type: " + mimeType + "\r\nContent-Length: " + content.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes());
        out.write(content);
        out.flush();
    }
}