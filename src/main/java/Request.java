import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final InputStream body;
    private final Map<String, List<String>> postParams;

    public Request(String method, String path, Map<String, String> headers, InputStream body) {
        this.method = method;
        this.path = path;
        this.headers = headers;

        Map<String, List<String>> params = new LinkedHashMap<>();
        InputStream resultBody = body;

        // парсим тело если Content-Type: application/x-www-form-urlencoded
        String contentType = headers.get("Content-Type");
        if (body != null && contentType != null
                && contentType.startsWith("application/x-www-form-urlencoded")) {
            try {
                String bodyString = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                List<NameValuePair> pairs = URLEncodedUtils.parse(bodyString, StandardCharsets.UTF_8);
                for (NameValuePair pair : pairs) {
                    params.computeIfAbsent(pair.getName(), k -> new ArrayList<>())
                          .add(pair.getValue());
                }
                // пересоздаём body чтобы handler мог его прочитать
                resultBody = new ByteArrayInputStream(bodyString.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                // оставляем body как есть
            }
        }

        this.body = resultBody;
        this.postParams = params;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public InputStream getBody() {
        return body;
    }

    public String getPostParam(String name) {
        List<String> values = postParams.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    public Map<String, List<String>> getPostParams() {
        return Collections.unmodifiableMap(postParams);
    }
}
