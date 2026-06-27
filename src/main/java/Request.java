import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.io.IOUtils;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final InputStream body;
    private final Map<String, List<PartInfo>> parts;

    public Request(String method, String path, Map<String, String> headers, InputStream body) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
        this.parts = new LinkedHashMap<>();

        String contentType = headers.get("Content-Type");
        if (body != null && contentType != null
                && contentType.startsWith("multipart/form-data")) {
            parseMultipart(contentType);
        }
    }

    private void parseMultipart(String contentType) {
        try {
            String contentLengthStr = headers.get("Content-Length");
            int contentLength = contentLengthStr != null ? Integer.parseInt(contentLengthStr.trim()) : -1;

            RequestContext ctx = new RequestContext() {
                public String getContentType() { return contentType; }
                public int getContentLength() { return contentLength; }
                public InputStream getInputStream() { return body; }
                public String getCharacterEncoding() { return "UTF-8"; }
            };

            FileUpload upload = new FileUpload();
            FileItemIterator iter = upload.getItemIterator(ctx);

            while (iter.hasNext()) {
                FileItemStream item = iter.next();
                String fieldName = item.getFieldName();
                boolean isFormField = item.isFormField();

                PartInfo part;
                if (isFormField) {
                    String value = IOUtils.toString(item.openStream(), "UTF-8");
                    part = new PartInfo(fieldName, value);
                } else {
                    byte[] fileBytes = IOUtils.toByteArray(item.openStream());
                    part = new PartInfo(fieldName, item.getName(), fileBytes);
                }

                parts.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(part);
            }
        } catch (Exception e) {
            System.err.println("Multipart parse error: " + e.getMessage());
        }
    }

    public String getMethod() { return method; }
    public String getPath() { return path; }
    public Map<String, String> getHeaders() { return headers; }
    public InputStream getBody() { return body; }

    public PartInfo getPart(String name) {
        List<PartInfo> list = parts.get(name);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    public Collection<PartInfo> getParts() {
        List<PartInfo> all = new ArrayList<>();
        for (List<PartInfo> list : parts.values()) {
            all.addAll(list);
        }
        return Collections.unmodifiableCollection(all);
    }

    // вспомогательный класс - часть multipart-запроса
    public static class PartInfo {
        private final String name;
        private final String fileName;
        private final byte[] content;
        private final String value;
        private final boolean isFile;

        public PartInfo(String name, String value) {
            this.name = name;
            this.fileName = null;
            this.value = value;
            this.content = value != null ? value.getBytes() : null;
            this.isFile = false;
        }

        public PartInfo(String name, String fileName, byte[] content) {
            this.name = name;
            this.fileName = fileName;
            this.content = content;
            this.value = null;
            this.isFile = true;
        }

        public String getName() { return name; }
        public String getFileName() { return fileName; }
        public byte[] getContent() { return content; }
        public String getValue() { return value; }
        public boolean isFile() { return isFile; }

        public String toString() {
            if (isFile) {
                return "Part{name=" + name + ", file=" + fileName
                        + ", size=" + (content != null ? content.length : 0) + "}";
            }
            return "Part{name=" + name + ", value=" + value + "}";
        }
    }
}
