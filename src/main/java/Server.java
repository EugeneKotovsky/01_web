import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
    private final Map<String, Map<String, Handler>> handlers;

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
             InputStream rawIn = clientSocket.getInputStream();
             BufferedOutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())) {

            // читаем заголовки до \r\n\r\n (работаем с сырыми байтами)
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int crlfCount = 0;
            int b;
            while ((b = rawIn.read()) != -1) {
                headerBuf.write(b);
                if (b == '\r' || b == '\n') {
                    crlfCount++;
                } else {
                    crlfCount = 0;
                }
                if (crlfCount == 4) break; // \r\n\r\n
            }

            String headerSection = headerBuf.toString(StandardCharsets.UTF_8);
            String[] lines = headerSection.split("\r\n");

            // request line
            if (lines.length == 0) return;
            final String[] parts = lines[0].split(" ");
            if (parts.length != 3) return;
            final String method = parts[0];
            final String path = parts[1];

            // headers
            final Map<String, String> headers = new HashMap<>();
            int contentLength = 0;
            for (int i = 1; i < lines.length; i++) {
                String[] hp = lines[i].split(": ", 2);
                if (hp.length == 2) {
                    headers.put(hp[0], hp[1]);
                    if (hp[0].equalsIgnoreCase("Content-Length")) {
                        contentLength = Integer.parseInt(hp[1].trim());
                    }
                }
            }

            // body — читаем сырые байты
            InputStream bodyStream = null;
            if (contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = rawIn.read(bodyBytes, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                bodyStream = new ByteArrayInputStream(bodyBytes);
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
