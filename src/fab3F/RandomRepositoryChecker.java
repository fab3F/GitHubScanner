package fab3F;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;


public class RandomRepositoryChecker implements Runnable{

    private final String name;
    private boolean exitThread;
    Thread thread;

    RandomRepositoryChecker() {
        this.name = "RandomRepositoryChecker-"+(Main.randomRepositoryCheckers.size()+1);
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
            if(!Main.RANDOM_REPOSITORY_URLS.isEmpty()){
                String url = Main.RANDOM_REPOSITORY_URLS.get(0);
                Main.RANDOM_REPOSITORY_URLS.remove(0);
                try {
                    searchInUrl(url);
                } catch (IOException e) {
                    if(Main.debug)
                        e.printStackTrace();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    if(Main.debug)
                        e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }else{
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    if(Main.debug)
                        e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
        System.out.println("[THREAD] " + name + " wurde gestoppt.");
    }

    public void searchInUrl(String repositoryUrl) throws IOException {
        if(repositoryUrl == null){
            System.out.println("[ERROR] Netzwerkfehler: repositoryUrl ist null");
            return;
        }

        String repositoryPath = repositoryUrl.replaceAll("https://github.com/", "");

        // API-URL zum Abrufen einer Liste von Dateien die discord.com/api/webhooks/ enthalten im Repository
        String apiUrl = "https://api.github.com/search/code?q=repo:" + repositoryPath + "+discord.com/api/webhooks/&type=code";
        // API-Anforderung an die GitHub-API senden
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "token " + Main.GITHUB_TOKEN)
                .build();
        String content = "{}";
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                content = response.body();
            } else{
                if(Main.debug)
                    System.out.println("[ERROR] Fehler beim API-Aufruf im Random Repository Checker. Statuscode: " + statusCode);
                Main.RANDOM_REPOSITORY_URLS.add(repositoryUrl);
                return;
            }
        } catch (IOException | InterruptedException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        if(content.contains("total_count\":0,")) {
            Main.noRepositoryWorker.getInstance().add(repositoryUrl);
            return;
        }

        boolean containsWebhook = false;
        List<String> fileUrls = getFileUrls(content);
        for (String fileUrl : fileUrls) {
            // API-Anforderung an die GitHub-API senden, um den Inhalt der Datei zu erhalten
            HttpURLConnection fileConnection = (HttpURLConnection) new URL(fileUrl).openConnection();
            fileConnection.setRequestProperty("Authorization", "Bearer " + Main.GITHUB_TOKEN);

            // API-Antwort lesen
            BufferedReader fileReader = new BufferedReader(new InputStreamReader(fileConnection.getInputStream()));
            String fileContent;
            StringBuilder fileContentBuilder = new StringBuilder();
            while ((fileContent = fileReader.readLine()) != null) {
                fileContentBuilder.append(fileContent);
            }
            fileReader.close();

            // Überprüfen, ob die Datei eine Discord-Webhook-URL enthält
            String fileContentString = fileContentBuilder.toString();
            Matcher matcher = Main.DISCORD_WEBHOOK_PATTERN.matcher(fileContentString);
            if (matcher.find()) {
                String webHookUrl = matcher.group();
                if(GeneralWebhookChecker.checkIfThisWorks(webHookUrl)) {
                    containsWebhook = true;
                    GeneralWebhookChecker.actionWebook(webHookUrl, repositoryUrl, fileUrl);
                    GeneralWebhookChecker.addRepository(repositoryUrl);
                    Main.FOUND_WEBHOOK_REPOSITORY_URLS.add(repositoryUrl);
                }
            }
        }
        if(!containsWebhook)
            Main.noRepositoryWorker.getInstance().add(repositoryUrl);
    }

    /**
     * Extrahiert die Datei-URLs aus der GitHub API-Antwort.
     */
    public static List<String> getFileUrls(String apiResponse) {
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
}