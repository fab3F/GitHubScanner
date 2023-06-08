package fab3F;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class OldRepositoryChecker implements Runnable{

    private final String name;
    private boolean exitThread;
    Thread thread;

    OldRepositoryChecker() {
        this.name = "OldRepositoryChecker";
        thread = new Thread(this, name);
        System.out.println("[THREAD] Neuer Thread erstellt: " + thread);
        exitThread = false;
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

            // Datei wird gechekt
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


    private void checkThem(){
        List<String> urls = new ArrayList<>();
        List<String> remainingUrls = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(Main.FILEPATH_GITHUB_REPOSITORY_WITH_WEBHOOK))) {
            String line;
            while ((line = reader.readLine()) != null) {
                urls.add(line);
            }
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }

        for(String url : urls) {
            try {
                if(foundInUrl(url) && !remainingUrls.contains(url)){ // Wenn eine Webhook url noch im Repository ist und die Url noch nicht drin ist (Duplikate ausschließen)
                    remainingUrls.add(url);
                }
            } catch (IOException e) {
                if(Main.debug)
                    e.printStackTrace();
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Main.FILEPATH_GITHUB_REPOSITORY_WITH_WEBHOOK, false))) {
            for (String remainingUrl : remainingUrls) {
                writer.write(remainingUrl);
                writer.newLine();
            }
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        System.out.println("[INFORMATION] Es wurden " + remainingUrls.size() + " GitHub Repositorys gefunden, die zum Zeitpunkt des Fundes funktionierende Discord Webhook Urls enthielten.");
    }



    public boolean foundInUrl(String repositoryUrl) throws IOException {
        boolean webHookStillInHere = false;

        String repositoryPath = repositoryUrl.replaceAll("https://github.com/", "");

        // API-URL zum Abrufen einer Liste von Dateien im Repository
        String apiUrl = String.format("https://api.github.com/repos/%s/contents", repositoryPath);
        // API-Anforderung an die GitHub-API senden
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestProperty("Authorization", "token " + Main.GITHUB_TOKEN);

        // API-Antwort lesen
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder content = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        reader.close();

        // Durchlaufe alle Dateien im Repository
        List<String> fileUrls = WebhookRepositoryChecker.getFileUrls(content.toString());
        for (String fileUrl : fileUrls) {
            // API-Anforderung an die GitHub-API senden, um den Inhalt der Datei zu erhalten
            HttpURLConnection fileConnection = (HttpURLConnection) new URL(fileUrl).openConnection();
            fileConnection.setRequestProperty("Authorization", "token " + Main.GITHUB_TOKEN);

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
                    GeneralWebhookChecker.actionWebook(webHookUrl, repositoryUrl, fileUrl);
                }
                // Wenn der Webhook nicht gültig ist, liegt das wahrscheinlich daran, dass der webhook ja von uns gelöscht wird
                // Solange aber der code im repo nicht verbessert wird, bleibt es in der liste
            }
        }

        return webHookStillInHere;
    }
}
