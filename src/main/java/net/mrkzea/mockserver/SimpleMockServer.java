package net.mrkzea.mockserver;


import javax.net.ServerSocketFactory;
import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SimpleMockServer extends Thread {


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MockServerConfig {
        MockResponse[] value();
    }


    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MockResponse {
        String url();

        String response();

        int statusCode() default 200;

        String contentType() default "application/json";

    }

    public SimpleMockServer(int port, long responseDelay, String packages) {

        if (serverStarted) {
            return;
        }
        annotatedMethods = getMethodsWithinPackage(packages);//getMethodsAnnotatedWith(SimpleMockServer.MockServerConfig.class)

        List<List<SimpleMockResponse>> mapped = annotatedMethods.stream().map(a -> processConfig(a.getAnnotation(SimpleMockServer.MockServerConfig.class).value())).collect(Collectors.toList());
        List<SimpleMockResponse> simpleMockResponses = mapped.stream().flatMap(list -> list.stream()).collect(Collectors.toList());
        setMockHttpServerResponses(simpleMockResponses.toArray(new SimpleMockResponse[simpleMockResponses.size()]));

        this.responseDelay = responseDelay;
        ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
        try {
            serverSocket = serverSocketFactory.createServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException("Could not construct server", e);
        }
        this.packages = packages;
        startServer();
    }


    private List<SimpleMockResponse> processConfig(MockResponse[] mocks) {
        return Arrays.asList(mocks)
                .stream()
                .map(m -> prepareResponse(m.url(), m.contentType(), m.response(), m.statusCode()))
                .collect(Collectors.toList());
    }


    public synchronized void startServer() {
        if (serverStarted) {
            return;
        }
        start();
        serverStarted = true;
        waitForServerToStart();
    }


    private synchronized void waitForServerToStart() {
        try {
            wait(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public SimpleMockResponse prepareResponse(String location, String contentType, String expectedResponseFile, int status) {
        SimpleMockResponse mockResponse = new SimpleMockResponse();
        mockResponse.setResponseContentType(contentType);
        InputStream s = getClass().getClassLoader().getResourceAsStream(expectedResponseFile);
        if (s == null) {
            mockResponse.setResponseCode(500);
        } else {
            String content = readStream(s);
            mockResponse.setResponseContent(content);
            mockResponse.setResponseUrl(location);
        }
        mockResponse.setResponseCode(status);
        return mockResponse;
    }


    public static class SimpleMockResponse {

        public int responseCode = 200;
        public Map<String, String> responseHeaders = new HashMap<String, String>();
        public byte[] responseContent = "received message".getBytes();
        public String responseContentType = "application/json;charset=utf-8";
        public String responseUrl;

        public void setMockResponseHeader(String name, String value) {
            responseHeaders.put(name, value);
        }

        public Map<String, String> getResponseHeaders() {
            return responseHeaders;
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public void setResponseContent(String content) {
            responseContent = content.getBytes();
        }

        public void setResponseContent(byte[] content) {
            responseContent = content;
        }


        public byte[] getResponseContent() {
            return responseContent;
        }

        public void setResponseContentType(String type) {
            responseContentType = type;
        }

        public String getResponseContentType() {
            return responseContentType;
        }

        public void setResponseUrl(String url) {
            this.responseUrl = url;
        }

        public String getResponseUrl() {
            return responseUrl;
        }

    }


    private Thread serverThread = null;
    private ServerSocket serverSocket = null;
    private volatile boolean serverStarted = false;
    private ServerSocketFactory serverSocketFactory = null;
    private int serverPort;
    private int readTimeOut = 5000;
    private int delayResponseTime = 0;
    private static byte[] NEW_LINE = "\r\n".getBytes();
    private String requestMethod = null;
    private String requestUrl = null;
    private Map<String, List<String>> requestHeaders = new HashMap<String, List<String>>();
    private ByteArrayOutputStream requestContent = new ByteArrayOutputStream();
    private Map<String, SimpleMockResponse> mockHttpServerResponses = new HashMap<String, SimpleMockResponse>();
    private AtomicInteger nrOfRequests = new AtomicInteger(0);
    private long responseDelay;
    private Set<Method> annotatedMethods;
    private String packages; // where the annotations are, for example "net.mrkzea.controller"


    private synchronized void waitForServerToStop() {
        try {
            wait(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public void run() {
        serverThread = Thread.currentThread();
        executeLoop();
    }


    private void executeLoop() {
        serverStarted();
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                HttpProcessor processor = new HttpProcessor(socket);
                processor.run();
            }
        } catch (IOException e) {
            if (e instanceof SocketException) {
                if (!("Socket closed".equalsIgnoreCase(e.getMessage()) || "Socket is closed"
                        .equalsIgnoreCase(e.getMessage()))) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } else {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } finally {
            serverStopped();
        }
    }


    private synchronized void serverStarted() {
        notifyAll();
    }


    private synchronized void serverStopped() {
        notifyAll();
    }


    public synchronized void stopServer() {
        requestUrl = null;
        mockHttpServerResponses = null;

        if (!serverStarted) {
            return;
        }

        try {
            serverStarted = false;
            serverThread.interrupt();
            serverSocket.close();
            waitForServerToStop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private class HttpProcessor {

        private Socket socket;

        public HttpProcessor(Socket socket) throws SocketException {
            socket.setSoTimeout(readTimeOut);
            socket.setKeepAlive(false);
            this.socket = socket;
        }

        public void run() {
            try {
                processRequest(socket);
                processResponse(socket);
            } catch (IOException e) {
                if (e instanceof SocketException) {
                    if (!("socket closed".equalsIgnoreCase(e.getMessage()))) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                } else {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } finally {
                try {
                    socket.shutdownOutput();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void processRequest(Socket socket) throws IOException {
            requestContent.reset();
            BufferedInputStream is = new BufferedInputStream(socket.getInputStream());
            String requestMethodHeader = new String(readLine(is));
            if (requestMethodHeader == null) {
                return;
            }
            processRequestMethod(requestMethodHeader);
            processRequestHeaders(is);
            processRequestContent(is);
        }

        private void processRequestMethod(String requestMethodHeader) {
            String[] parts = requestMethodHeader.split(" ");
            if (parts.length < 2) {
                throw new RuntimeException("illegal http request");
            }
            requestMethod = parts[0];
            requestUrl = parts[1];
        }

        private void processRequestHeaders(InputStream is) throws IOException {
            requestHeaders.clear();
            System.out.println(requestUrl);
            byte[] line = null;
            while ((line = readLine(is)) != null) {
                String lineStr = new String(line);
                if ("".equals(lineStr.trim())) {
                    break;
                }
                addRequestHeader(lineStr);
            }
        }

        private void processRequestContent(InputStream is) throws NumberFormatException,
                IOException {
            if (!("PUT".equals(requestMethod) || "POST".equals(requestMethod))) {
                return;
            }

            List<String> transferEncodingValues = requestHeaders.get("Transfer-Encoding");
            String transferEncoding =
                    (transferEncodingValues == null || transferEncodingValues.isEmpty()) ? null
                            : transferEncodingValues.get(0);
            if ("chunked".equals(transferEncoding)) {
                processChunkedContent(is);
            } else {
                processRegularContent(is);
            }
        }

        private void processRegularContent(InputStream is) throws IOException {
            List<String> contentLengthValues = requestHeaders.get("Content-Length");
            String contentLength =
                    (contentLengthValues == null || contentLengthValues.isEmpty()) ? null
                            : contentLengthValues.get(0);
            if (contentLength == null) {
                return;
            }
            int contentLen = Integer.parseInt(contentLength);
            byte[] bytes = new byte[contentLen];
            is.read(bytes);
            requestContent.write(bytes);
        }


        private void processChunkedContent(InputStream is) throws IOException {
            requestContent.write("".getBytes());
            byte[] chunk = null;
            byte[] line = null;
            boolean lastChunk = false;
            while (!lastChunk && (line = readLine(is)) != null) {

                String lineStr = new String(line);
                if ("0".equals(lineStr)) {
                    lastChunk = true;
                }

                if (!lastChunk) {
                    int chunkLen = Integer.parseInt(lineStr, 16);
                    chunk = getChunk(is, chunkLen);
                    readLine(is);
                    requestContent.write(chunk);
                }
            }
            if (lastChunk) {
                readLine(is);
            }
        }


        private byte[] readLine(InputStream is) throws IOException {
            int n;
            ByteArrayOutputStream tmpOs = new ByteArrayOutputStream();
            while ((n = is.read()) != -1) {
                if (n == '\r') {
                    n = is.read();
                    if (n == '\n') {
                        return tmpOs.toByteArray();
                    } else {
                        tmpOs.write('\r');
                        if (n != -1) {
                            tmpOs.write(n);
                        } else {
                            return tmpOs.toByteArray();
                        }
                    }
                } else if (n == '\n') {
                    return tmpOs.toByteArray();
                } else {
                    tmpOs.write(n);
                }
            }
            return tmpOs.toByteArray();
        }

        private byte[] getChunk(InputStream is, int len) throws IOException {
            ByteArrayOutputStream chunk = new ByteArrayOutputStream();
            int read = 0;
            int totalRead = 0;
            byte[] bytes = new byte[512];
            while (totalRead < len) {
                read = is.read(bytes, 0, Math.min(bytes.length, len - totalRead));
                chunk.write(bytes, 0, read);
                totalRead += read;
            }
            return chunk.toByteArray();
        }

        private void addRequestHeader(String line) {
            String[] parts = line.split(": ");
            List<String> values = requestHeaders.get(parts[0]);
            if (values == null) {
                values = new ArrayList<String>();
                requestHeaders.put(parts[0], values);
            }
            values.add(parts[1]);
        }

        private void processResponse(Socket socket) throws IOException {
            if (!delayResponse())
                return;

            OutputStream sos = socket.getOutputStream();
            BufferedOutputStream os = new BufferedOutputStream(sos);
            SimpleMockResponse mockHttpServerResponse = mockHttpServerResponses.get(requestUrl);
            if (mockHttpServerResponse != null) {
                int mockResponseCode = mockHttpServerResponse.getResponseCode();
                os.write(("HTTP/1.1 " + mockResponseCode).getBytes());
            } else {
                os.write(("HTTP/1.1 " + "500").getBytes());
            }
            os.write(NEW_LINE);
            processResponseHeaders(os);
            processResponseContent(os);
            os.flush();
            nrOfRequests.getAndAdd(1);
        }

        private boolean delayResponse() {
            if (delayResponseTime > 0) {
                try {
                    Thread.sleep(delayResponseTime);
                    return true;
                } catch (InterruptedException e) {
                    return false;
                }
            }
            return true;
        }

        private void processResponseContent(OutputStream os) throws IOException {
            if (mockHttpServerResponses.get(requestUrl) != null) {
                if (mockHttpServerResponses.get(requestUrl).getResponseContent() == null) {
                    return;
                }
                os.write(mockHttpServerResponses.get(requestUrl).getResponseContent());
            }
        }

        private void processResponseHeaders(OutputStream os) throws IOException {
            addServerResponseHeaders();
            System.out.println(requestUrl);
            if (mockHttpServerResponses.get(requestUrl) != null) {

                mockHttpServerResponses.get(requestUrl).getResponseHeaders().keySet()
                        .stream()
                        .forEach(header -> writeToOutput(os, header));

                os.write(NEW_LINE);
            }
        }

        private void addServerResponseHeaders() throws IOException {
            SimpleMockResponse response = mockHttpServerResponses.get(requestUrl);
            if (response != null) {
                Map<String, String> mockResponseHeaders = response.getResponseHeaders();
                mockResponseHeaders.put("Content-Type", response.getResponseContentType());
                mockResponseHeaders.put("Content-Length", response.getResponseContent().length + "");
                mockResponseHeaders.put("Server", "Mock HTTP Server v1.0");
                mockResponseHeaders.put("Connection", "closed");
            }
        }
    }

    private void writeToOutput(OutputStream os, String header) {
        try {
            os.write((header + ": " + mockHttpServerResponses.get(requestUrl)
                    .getResponseHeaders()
                    .get(header))
                    .getBytes());
            os.write(NEW_LINE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to IO", e);
        }
    }

    public String readStream(InputStream in) {
        StringBuffer expectedBuff = new StringBuffer();

        try {
            BufferedReader i = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = i.readLine()) != null) {
                expectedBuff.append(line);
            }
            i.close();
        } catch (IOException e) {
            System.out.println("ERROR: Unable to read stream!");
        }
        return expectedBuff.toString();
    }


    public void setReadTimeout(int milliseconds) {
        readTimeOut = milliseconds;
    }

    public void setDelayResponse(int milliseconds) {
        delayResponseTime = milliseconds;
    }

    public String getRequestContentAsString() {
        return requestContent.toString();
    }

    public byte[] getRequestContent() {
        return requestContent.toByteArray();
    }

    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }


    public void setMockHttpServerResponses(SimpleMockResponse... responses) {
        mockHttpServerResponses.clear();
        for (int i = 0; i < responses.length; i++) {
            mockHttpServerResponses.put(responses[i].getResponseUrl(), responses[i]);
        }
    }

    public void updateMockHttpServerResponse(SimpleMockResponse response) {
        mockHttpServerResponses.put(response.getResponseUrl(), response);
    }

    public void clearResponses() {
        mockHttpServerResponses.clear();
    }


    public Map<String, SimpleMockResponse> getMockHttpServerResponses() {
        return mockHttpServerResponses;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getNrOfRequests() {
        return nrOfRequests.get();
    }


    public Set<Method> getMethodsWithinPackage(String pack) {
        Class[] classes;
        try {
            classes = getClasses(pack);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Set<Set<Method>> collected = Arrays.asList(classes)
                .stream()
                .map(c -> getMethodsFromTypeAnnotatedWith(c, MockServerConfig.class))
                .collect(Collectors.toSet());

        return collected.stream().flatMap(s -> s.stream()).collect(Collectors.toSet());
    }


    public Set<Method> getMethodsFromTypeAnnotatedWith(final Class<?> type, final Class<? extends Annotation> annotation) {
        final Set<Method> methods = new HashSet<>();
        Class<?> clazz = type;
        while (clazz != Object.class && clazz != null) {
            Arrays.asList(clazz.getDeclaredMethods()).stream().filter(m -> annotation == null || m.isAnnotationPresent(annotation)).forEach(m -> methods.add(m));
            clazz = clazz.getSuperclass();
        }
        return methods;
    }


    private Class[] getClasses(String packageName)
            throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class> classes = new ArrayList<>();
        dirs.stream().forEach(d -> classes.addAll(findClasses(d, packageName)));
        return classes.toArray(new Class[classes.size()]);
    }


    private List<Class> findClasses(File directory, String packageName) {
        List<Class> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }

        File[] files = directory.listFiles();

        Arrays.asList(directory.listFiles())
                .stream()
                .filter(f -> f.isDirectory())
                .forEach(f -> classes.addAll(findClasses(f, packageName + "." + f.getName())));

        Arrays.asList(files)
                .stream()
                .filter(file -> isFileAClass(file))
                .forEach(file -> {
                    try {
                        classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });

        return classes;
    }

    private boolean isFileAClass(File file) {
        return file.getName().endsWith(".class");
    }

}
