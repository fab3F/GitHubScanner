package fab3F;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;


public class WebhookFileChecker implements Runnable{

    Queue<String> folders;
    private final String name;
    private boolean exitThread;
    Thread thread;

    WebhookFileChecker() {
        this.name = "WebhookFileChecker-"+(Main.webhookFileCheckers.size()+1);
        thread = new Thread(this, name);
        System.out.println("[THREAD] Neuer Thread erstellt: " + thread);
        exitThread = false;
        folders = new LinkedList<String>();
        thread.start();
    }

    public void stopThread() {
        exitThread = true;
    }

    @Override
    public void run() {

        while (!exitThread){
            /*
            if(!this.folders.isEmpty()){
                String url = this.folders.remove();
                try {
                    searchInRepositoryUrl(url);
                } catch (IOException e) {
                    if(Main.debug)
                        e.printStackTrace();
                }
            }else
             */
            if(!Main.WEBHOOK_FILE_URLS.isEmpty()){
                String url = Main.WEBHOOK_FILE_URLS.get(0);
                Main.WEBHOOK_FILE_URLS.remove(0);
                try {
                    searchInFileUrl(url);
                } catch (IOException e) {
                    if(Main.debug)
                        e.printStackTrace();
                }
            }else{
                try {
                    Thread.sleep(Main.WEBHOOK_FILE_CHECKER_WAIT * 1000L); //  Pause machen
                } catch (InterruptedException e) {
                    if(Main.debug)
                        e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
        System.out.println("[THREAD] " + name + " wurde gestoppt.");
    }

    public void searchInFileUrl(String fileUrl) throws IOException{

        if(fileUrl == null)
            return;

        fileUrl = fileUrl.replaceAll(" ", "%20").replaceAll("\"", "%22");
        HttpURLConnection fileConnection = (HttpURLConnection) new URL(fileUrl).openConnection();

        // API-Antwort lesen
        BufferedReader fileReader;
        try{
            fileReader = new BufferedReader(new InputStreamReader(fileConnection.getInputStream()));
        }catch (IOException ignored1){
            HttpURLConnection fileConnection2 = (HttpURLConnection) new URL(fileUrl.replace("/master/", "/main/")).openConnection();
            try{
                fileReader = new BufferedReader(new InputStreamReader(fileConnection2.getInputStream()));
            }catch (IOException ignored2){
                return;
            }
        }

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
            if (GeneralWebhookChecker.checkIfThisWorks(webHookUrl)) {
                GeneralWebhookChecker.actionWebook(webHookUrl, fileUrl);
                GeneralWebhookChecker.addFile(fileUrl);
                Main.FOUND_WEBHOOK_FILE_URLS.add(fileUrl);
            } else {
                Main.noFileWorker.getInstance().toWork.add(new NoFileWorker.FileObject(fileUrl, true));
            }
        }


    }

    public void searchInRepositoryUrl(String repositoryUrl) throws IOException {
        // Repository URL kann auch eine Ordner url sein

        if(repositoryUrl == null){
            System.out.println("[ERROR] Netzwerkfehler: repositoryUrl ist null");
            return;
        }

        // API-URL zum Abrufen einer Liste von Dateien im Repository
        String apiUrl = getUrl(repositoryUrl);

        repositoryUrl = extractRepositoryUrl(repositoryUrl);

        // API-Anforderung an die GitHub-API senden
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("Authorization", "Bearer " + Main.GITHUB_TOKEN);

        // API-Antwort lesen
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        String content = sb.toString();
        List<String> fileUrls = getFileUrls(content);

        boolean containsWebhook = false;
        // Durchlaufe alle Dateien im Repository
        for (String fileUrl : fileUrls) {
            //System.out.println("[INFORMATION] Checking File: " + fileUrl);

            // API-Anforderung an die GitHub-API senden, um den Inhalt der Datei zu erhalten
            HttpURLConnection fileConnection = (HttpURLConnection) new URL(fileUrl).openConnection();

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
                if (GeneralWebhookChecker.checkIfThisWorks(webHookUrl)) {
                    containsWebhook = true;
                    GeneralWebhookChecker.actionWebook(webHookUrl, fileUrl);
                    //GeneralWebhookChecker.addRepository(repositoryUrl);
                    //Main.FOUND_WEBHOOK_REPOSITORY_URLS.add(repositoryUrl);
                }
            }
        }

        if (!containsWebhook)
            Main.noFileWorker.getInstance().toWork.add(new NoFileWorker.FileObject(repositoryUrl, true));
    }

    /**
     * Extrahiert die Datei-URLs aus der GitHub API-Antwort.
     */
    public List<String> getFileUrls(String apiResponse) {
        List<String> urls = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(apiResponse);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if(jsonObject.getString("html_url").contains("/tree/")){
                    this.folders.add(jsonObject.getString("html_url"));
                }else{
                    String rawUrl = jsonObject.getString("download_url");
                    urls.add(rawUrl);
                }
            }
        } catch (JSONException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return urls;
    }

    public static String getUrl(String url) {
        // Entferne das "https://github.com/"-Präfix
        String repositoryUrl = url.replace("https://github.com/", "");

        if(!repositoryUrl.contains("/tree/")){
            return "https://api.github.com/repos/" + repositoryUrl + "/contents";
        }

        // Ersetze "/tree/IRGENDWAS"
        repositoryUrl = repositoryUrl.replaceAll("/tree/[^/]+", "/contents/");

        // Füge das "https://api.github.com/repos/"-Präfix hinzu
        String apiUrl = "https://api.github.com/repos/" + repositoryUrl;

        return apiUrl;
    }

    public static String extractRepositoryUrl(String fileUrl) {
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