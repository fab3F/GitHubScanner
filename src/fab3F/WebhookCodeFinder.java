package fab3F;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

            if(Main.WEBHOOK_FILE_URLS.size() < Main.WEBHOOK_FILE_FINDER_AMOUNT_MAX_URLS_IN_LIST){
                List<String> gitHubUrls = new ArrayList<>();

                String apiUrl = "https://sourcegraph.com/.api/search/stream";
                String url_1 = "/discord(?:app)?\\.com\\/api\\/webhooks\\/";
                List<String> url_2 = new ArrayList<>();
                url_2.add("[0-9]+\\/");
                url_2.add("[0-9]{17}\\/");
                url_2.add("[0-9]{18}\\/");
                url_2.add("[0-9]{19}\\/");
                url_2.add("[0-9]{20}\\/");
                url_2.add("[\\w-]+\\/");

                List<String> url_3 = new ArrayList<>();
                url_3.add("[a-zA-Z0-9-_]+/");
                url_3.add("[a-zA-Z0-9-_]{67}/");
                url_3.add("[a-zA-Z0-9-_]{68}/");
                url_3.add("[\\w-]+/");

                String regex = url_1 + url_2.get(Main.random.nextInt(url_2.size())) + url_3.get(Main.random.nextInt(url_3.size()));

                String filters = "context:global count:all fork:yes archived:yes";
                String query = filters + " " + regex;

                try {
                    String encodedQuery = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
                    String fullUrl = apiUrl + "?q=" + encodedQuery + "&display=-1";
                    URL url = new URL(fullUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "text/event-stream");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String line;
                        boolean isInEventMatches = false;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("event: matches")) {
                                isInEventMatches = true;
                            } else if (line.startsWith("event: ")) {
                                isInEventMatches = false;
                            }
                            if (isInEventMatches && line.startsWith("data: ")) {
                                String jsonData = line.substring("data: ".length());
                                for(String gitHubUrl : extractLinks(jsonData)){
                                    if(!gitHubUrls.contains(gitHubUrl))
                                        gitHubUrls.add(gitHubUrl);
                                }
                            }
                        }
                        reader.close();
                    } else {
                        if(Main.debug)
                            System.out.println("[ERROR] Fehler beim API-Aufruf im Webhook Repository Finder. Statuscode: " + responseCode);
                    }
                    connection.disconnect();
                } catch (IOException e) {
                    if(Main.debug)
                        e.printStackTrace();
                }

                for(String gitHubUrl : gitHubUrls){
                    if(!Main.WEBHOOK_FILE_URLS.contains(gitHubUrl) && Main.noFileWorker.getInstance().haveToCheckThis(gitHubUrl))
                        Main.WEBHOOK_FILE_URLS.add(gitHubUrl);
                }
                gitHubUrls.clear();
                //System.out.println(Main.WEBHOOK_REPOSITORY_URLS.size());

            }else{

                try {
                    Thread.sleep(Main.WEBHOOK_FILE_FINDER_WAIT * 1000L); // 100 Sekunden Pause
                } catch (InterruptedException e) {
                    if(Main.debug)
                        e.printStackTrace();
                    Thread.currentThread().interrupt();
                }

            }
        }
        System.out.println("[THREAD] " + name + " wurde gestoppt.");
    }


    /**
     * Extrahiert die Repository-URLs aus der Sourcegraph API-Antwort.
     */
    public static List<String> extractLinks(String response) {
        List<String> urls = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(response);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if(jsonObject.getString("repository").contains("github.com")){
                    String rawUrl = "https://raw.githubusercontent.com" + jsonObject.getString("repository").replaceAll("github.com", "") + "/master/" + jsonObject.getString("path");
                    urls.add(rawUrl);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return urls;
    }

}
