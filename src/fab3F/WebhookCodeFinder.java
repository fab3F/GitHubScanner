package fab3F;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class WebhookCodeFinder implements Runnable{

    private final String name;
    private boolean exitThread;
    Thread thread;

    WebhookCodeFinder() {
        this.name = "WebhookCodeFinder-"+(Main.webhookCodeFinders.size()+1);
        thread = new Thread(this, name);
        System.out.println("[THREAD] Neuer Thread erstellt: " + thread);
        exitThread = false;
        thread.start();
    }

    public void stopThread() {
        exitThread = true;
    }

    @Override
    public void run() {
        while (!exitThread){

            if(Main.WEBHOOK_REPOSITORY_URLS.size() < Main.WEBHOOK_REPOSITORY_FINDER_AMOUNT_MAX_URLS_IN_LIST){
                List<String> gitHubUrls = new ArrayList<>();
                String token = Main.GITHUB_TOKEN;
                int currentPage = 1;

                do {
                    String apiUrl = "https://api.github.com/search/code?q=discord.com%2Fapi%2Fwebhooks&type=code&sort=updated&per_page=100&page=" + currentPage;
                    HttpClient client = HttpClient.newBuilder().build();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(apiUrl))
                            .header("Authorization", "Bearer " + token)
                            .build();

                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        int statusCode = response.statusCode();

                        if (statusCode == 200) {
                            for(String gitHubFileUrl : extractLinks(response.body())) {
                                String gitHubUrl = extractRepositoryUrl(gitHubFileUrl);
                                if (!gitHubUrls.contains(gitHubUrl)) {
                                    gitHubUrls.add(gitHubUrl);
                                }
                            }
                        } else {
                            if(Main.debug)
                                System.out.println("[ERROR] Fehler beim API-Aufruf im Webhook Repository Finder. Statuscode: " + statusCode);
                        }

                    } catch (IOException | InterruptedException e) {
                        if(Main.debug)
                            e.printStackTrace();
                    }

                    currentPage++;
                } while (currentPage <= (Main.WEBHOOK_REPOSITORY_FINDER_AMOUNT_PER_REQUEST/100) && gitHubUrls.size() < Main.WEBHOOK_REPOSITORY_FINDER_AMOUNT_PER_REQUEST);

                for(String url : gitHubUrls){
                    if(!Main.WEBHOOK_REPOSITORY_URLS.contains(url) && Main.noRepositoryWorker.getInstance().haveToCheckThis(url))
                        Main.WEBHOOK_REPOSITORY_URLS.add(url);
                }
                gitHubUrls.clear();
                //System.out.println(Main.WEBHOOK_REPOSITORY_URLS.size());

            }

        }
        System.out.println("[THREAD] " + name + " wurde gestoppt.");
    }

    /**
     * Extrahiert die Datei-URLs aus der GitHub API-Antwort.
     */
    public static List<String> extractLinks(String apiResponse) {
        List<String> urls = new ArrayList<>();
        try {
            JSONObject responseJson = new JSONObject(apiResponse);
            JSONArray jsonArray = responseJson.getJSONArray("items");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String htmlUrl = jsonObject.getString("html_url");
                urls.add(htmlUrl);
            }
        } catch (JSONException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return urls;
    }

    public static String extractRepositoryUrl(String fileUrl) {
        fileUrl = fileUrl.replaceAll("\\[" , "%5B").replaceAll("\\]", "%5D");
        try {
            URI uri = new URI(fileUrl);
            String host = uri.getHost();
            String[] segments = uri.getPath().split("/");
            if (segments.length >= 3) {
                String owner = segments[1];
                String repo = segments[2];
                return "https://" + host + "/" + owner + "/" + repo;
            }
        } catch (URISyntaxException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return null;
    }

}
