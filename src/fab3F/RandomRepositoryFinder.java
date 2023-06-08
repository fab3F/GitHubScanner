package fab3F;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class RandomRepositoryFinder implements Runnable{

    private final String name;
    private boolean exitThread;
    Thread thread;

    RandomRepositoryFinder() {
        this.name = "RandomRepositoryFinder-"+(Main.randomRepositoryFinders.size()+1);
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

            if(Main.RANDOM_REPOSITORY_URLS.size()<Main.RANDOM_REPOSITORY_FINDER_AMOUNT_MAX_URLS_IN_LIST){
                int amount = 100;
                if(Main.RANDOM_REPOSITORY_FINDER_AMOUNT_PER_REQUEST>=1 && Main.RANDOM_REPOSITORY_FINDER_AMOUNT_PER_REQUEST<=100){
                    amount = Main.RANDOM_REPOSITORY_FINDER_AMOUNT_PER_REQUEST;
                }
                List<String> list = getUrls(amount, Main.charList.get(Main.random.nextInt(Main.charList.size())));
                list.removeIf(url -> Main.RANDOM_REPOSITORY_URLS.contains(url) || !Main.noRepositoryWorker.getInstance().haveToCheckThis(url));
                Main.RANDOM_REPOSITORY_URLS.addAll(list);
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

    private static List<String> getUrls(int amount, char letter){
        String token = Main.GITHUB_TOKEN;
        String apiUrl = "https://api.github.com/search/repositories?q=" + letter + "&type=repositories&sort=updated&per_page=" + amount;
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "token " + token)
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                return extractLinks(response.body());
            } else {
                System.out.println("[ERROR] Fehler beim API-Aufruf im Random Repository Finder. Statuscode: " + statusCode);
            }
        } catch (IOException | InterruptedException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static List<String> extractLinks(String responseBody) {
        List<String> links = new ArrayList<>();
        try{
            JSONObject responseJson = new JSONObject(responseBody);
            JSONArray itemsArray = responseJson.getJSONArray("items");
            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject repositoryObj = itemsArray.getJSONObject(i);
                String repositoryUrl = repositoryObj.getString("html_url");
                links.add(repositoryUrl);
            }
        } catch (
        JSONException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return links;
    }

}
