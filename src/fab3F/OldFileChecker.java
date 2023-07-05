package fab3F;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;

public class OldFileChecker implements Runnable{

    List<String> files;
    Queue<String> folders;

    private final String name;
    private boolean exitThread;
    Thread thread;

    OldFileChecker() {
        this.name = "OldFileChecker";
        thread = new Thread(this, name);
        System.out.println("[THREAD] Neuer Thread erstellt: " + thread);
        exitThread = false;
        files = new ArrayList<>();
        folders = new LinkedList<String>();
        thread.start();
    }

    public void stopThread() {
        exitThread = true;
        thread.interrupt(); // Thread unterbrechen, um ihn sofort zu beenden
    }

    @Override
    public void run() {
        while (!exitThread && !Thread.currentThread().isInterrupted()) {

            System.out.println("[WICHTIG] Gerade werden funktionierende URLS verschoben und geprüft. DIESER VORGANG DARF NICHT UNTERBROCHEN WERDEN! WARTEN SIE, BIS DER VORGANG ABGESCHLOSSEN IST.");
            Main.dontClose = true;

            // Datein werden gechekt
            checkThem();

            System.out.println("[WICHTIG] Der Vorgang ist abgeschlossen.");
            Main.dontClose = false;


            try {
                Thread.sleep(2 * 60 * 60 * 1000); // alle 2 Stunden
            } catch (InterruptedException e) {
                if(Main.debug)
                    e.printStackTrace();
                // InterruptedExcpetion abfangen, um den Thread zu beenden
                Thread.currentThread().interrupt(); // Setzt den Interrupt-Status erneut
            }


        }
        System.out.println("[THREAD] " + name + " wurde gestoppt.");
    }


    public void checkThem(){
        Main.oldFileChecker.files = new ArrayList<>();
        Main.oldFileChecker.folders = new LinkedList<String>();
        List<String> remainingUrls = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(Main.FILEPATH_GITHUB_FILE_WITH_WEBHOOK))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Main.oldFileChecker.files.add(line);
            }
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }

        for (String fileUrl : Main.oldFileChecker.files){
            try {
                if(foundInFileUrl(fileUrl) && !remainingUrls.contains(fileUrl)){ // Wenn eine Webhook url noch im File ist und die Url noch nicht drin ist (Duplikate ausschließen)
                    remainingUrls.add(fileUrl);
                }
            } catch (IOException e) {
                if(Main.debug)
                    e.printStackTrace();
            }

            /*
            while(!Main.oldFileChecker.folders.isEmpty()){
                String folder = folders.remove();
                try {
                    if(foundInRepositoryUrl(folder) && !remainingUrls.contains(extractRepositoryUrl(folder))){ // Wenn eine Webhook url noch im Repository ist und die Url noch nicht drin ist (Duplikate ausschließen)
                        remainingUrls.add(extractRepositoryUrl(folder));
                    }
                } catch (IOException e) {
                    if(Main.debug)
                        e.printStackTrace();
                }
            }
            */

        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Main.FILEPATH_GITHUB_FILE_WITH_WEBHOOK, false))) {
            for (String remainingUrl : remainingUrls) {
                writer.write(remainingUrl);
                writer.newLine();
            }
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        System.out.println("[INFORMATION] Es wurden " + remainingUrls.size() + " GitHub Files gefunden, die zum Zeitpunkt des Fundes funktionierende Discord Webhook Urls enthielten.");
    }

    public boolean foundInFileUrl(String fileUrl) throws IOException{

        fileUrl = fileUrl.replaceAll(" ", "%20").replaceAll("\"", "%22");
        HttpURLConnection fileConnection = (HttpURLConnection) new URL(fileUrl).openConnection();

        // API-Antwort lesen
        BufferedReader fileReader;
        try{
            fileReader = new BufferedReader(new InputStreamReader(fileConnection.getInputStream()));
        }catch (FileNotFoundException ignored){
            HttpURLConnection fileConnection2 = (HttpURLConnection) new URL(fileUrl.replace("/master/", "/main/")).openConnection();
            fileReader = new BufferedReader(new InputStreamReader(fileConnection2.getInputStream()));
        }

        boolean webHookStillInHere = false;

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
            webHookStillInHere = true;
            String webHookUrl = matcher.group();
            if(GeneralWebhookChecker.checkIfThisWorks(webHookUrl)) {
                GeneralWebhookChecker.actionWebook(webHookUrl, fileUrl);
            }
            // Wenn der Webhook nicht gültig ist, liegt das wahrscheinlich daran, dass der webhook ja von uns gelöscht wird
            // Solange aber der code im file nicht verbessert wird, bleibt es in der liste
        }
        return webHookStillInHere;
    }

    public boolean foundInRepositoryUrl(String repositoryUrl) throws IOException {
        // Repository URL kann auch eine Ordner url sein

        boolean webHookStillInHere = false;

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

        // Durchlaufe alle Dateien im Repository
        List<String> fileUrls = getFileUrls(content);

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
                webHookStillInHere = true;
                String webHookUrl = matcher.group();
                if(GeneralWebhookChecker.checkIfThisWorks(webHookUrl)) {
                    GeneralWebhookChecker.actionWebook(webHookUrl, fileUrl);
                }
                // Wenn der Webhook nicht gültig ist, liegt das wahrscheinlich daran, dass der webhook ja von uns gelöscht wird
                // Solange aber der code im repo nicht verbessert wird, bleibt es in der liste
            }
        }

        return webHookStillInHere;
    }

    /**
     * Extrahiert die Datei-URLs aus der GitHub API-Antwort.
     */
    public static List<String> getFileUrls(String apiResponse) {
        List<String> raw_urls = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(apiResponse);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if(jsonObject.getString("html_url").contains("/tree/")){
                    Main.oldFileChecker.folders.add(jsonObject.getString("html_url"));
                }else{
                    String rawUrl = jsonObject.getString("download_url");
                    raw_urls.add(rawUrl);
                }
            }
        } catch (JSONException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return raw_urls;
    }

    public static String getUrl(String url) {
        // Entferne das "https://github.com/"-Präfix
        String repositoryUrl = url.replace("https://github.com/", "");

        if(!repositoryUrl.contains("/tree/")){
            return "https://api.github.com/repos/" + repositoryUrl + "/contents";
        }

        // Ersetze "/tree/IRGENDWAS"
        repositoryUrl = repositoryUrl.replaceAll("/tree/[^/]+", "/contents");

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
