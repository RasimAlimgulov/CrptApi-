import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {
    private final HttpClient client;
    private final String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final Semaphore semaphore;
    private final Lock lock;
    private final int requestLimit;
    private final TimeUnit timeUnit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.client = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.semaphore = new Semaphore(requestLimit);
        this.lock = new ReentrantLock();
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;

        scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                semaphore.release(requestLimit - semaphore.availablePermits());
            } finally {
                lock.unlock();
            }
        }, 0, 1, timeUnit);

    }

    public void createDocument(Document document, String signature) throws InterruptedException, JsonProcessingException, ExecutionException, TimeoutException {
        semaphore.acquire();

        String jsonBody = objectMapper.writeValueAsString(document);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type = "LP_INTRODUCE_GOODS";
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public static void main(String[] args) throws Exception {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        Document doc = new Document();


        String signature = "your-signature";

        api.createDocument(doc, signature);
        api.shutdown();
    }
}
