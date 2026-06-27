import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final InputStream body;
    private final Map<String, List<String>> queryParams;

    public Request(String method, String path, Map<String, String> headers, InputStream body) {
        this.method = method;
        this.headers = headers;
        this.body = body;

        String pathOnly = path;
        Map<String, List<String>> params = new LinkedHashMap<>();

        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            pathOnly = path.substring(0, queryIndex);
            try {
                URI uri = new URI("http://localhost" + path);
                List<NameValuePair> pairs = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8.name());
                for (NameValuePair pair : pairs) {
                    params.computeIfAbsent(pair.getName(), k -> new ArrayList<>())
                          .add(pair.getValue());
                }
            } catch (URISyntaxException e) {
                // оставляем пустую карту параметров
            }
        }

        this.path = pathOnly;
        this.queryParams = Collections.unmodifiableMap(params);
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

    public String getQueryParam(String name) {
        List<String> values = queryParams.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }
}
