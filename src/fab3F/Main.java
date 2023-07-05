package fab3F;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class Main {

    public static final String configFilePath = "config.json";
    public static JSONObject config;

    public static boolean debug; // Bei false werden Fehler ignoriert
    public static String GITHUB_TOKEN; // nur 90 Tage gültig Stand 4.6.23
    public static String LOG_WEBHOOK_TOKEN;
    public static String LOG_WEBHOOK_URL;

    public static int WEBHOOK_FILE_FINDER_AMOUNT_MAX_URLS_IN_LIST; // Maximale Anzahl an Urls, die in der Liste sein sollen. Erst wenn weniger drin sind, werden neue hinzugefügt
    public static int WEBHOOK_FILE_FINDER_AMOUNT; // Anzahl an Webhook File Findern
    public static int WEBHOOK_FILE_CHECKER_AMOUNT; // Anzahl an Webhook File Checkern (am besten immer pro finder 10 checker)

    public static String FILEPATH_GITHUB_FILE_WITH_WEBHOOK;
    public static String FILEPATH_GITHUB_FILES_NO_WEBHOOK;

    public static final Random random = new Random();
    public static final Pattern DISCORD_WEBHOOK_PATTERN = Pattern.compile("https://discord(?:app)?\\.com/api/webhooks/[\\w-]+/[\\w-]+");

    public static List<String> WEBHOOK_FILE_URLS = new ArrayList<>(); // Liste mit File Urls, die das webhook pattern enthalten
    public static List<String> FOUND_WEBHOOK_FILE_URLS = new ArrayList<>(); // Liste mit Github File Urls, die einen funktioniernden Webhook enthalten (temporär)

    public static boolean dontClose = false;

    public static List<WebhookCodeFinder> webhookCodeFinders = new ArrayList<>();
    public static List<WebhookFileChecker> webhookFileCheckers = new ArrayList<>();
    public static OldFileChecker oldFileChecker;
    public static NoFileWorker noFileWorker;

    public static void main(String[] args) {
        // Konfiguartion
        config = readConfigFromFile(configFilePath);
        if (config == null) {
            System.out.println("[ERROR] Fehler beim Lesen der Konfigurationsdatei.");
            return;
        }
        debug = config.getBoolean("debug");
        GITHUB_TOKEN = config.getString("GITHUB_TOKEN");
        LOG_WEBHOOK_TOKEN = config.getString("LOG_WEBHOOK_TOKEN");
        LOG_WEBHOOK_URL = config.getString("LOG_WEBHOOK_URL");
        WEBHOOK_FILE_FINDER_AMOUNT_MAX_URLS_IN_LIST = config.getInt("WEBHOOK_FILE_FINDER_AMOUNT_MAX_URLS_IN_LIST");
        WEBHOOK_FILE_FINDER_AMOUNT = config.getInt("WEBHOOK_FILE_FINDER_AMOUNT");
        WEBHOOK_FILE_CHECKER_AMOUNT = config.getInt("WEBHOOK_FILE_CHECKER_AMOUNT");
        FILEPATH_GITHUB_FILE_WITH_WEBHOOK = config.getString("FILEPATH_GITHUB_FILE_WITH_WEBHOOK");
        FILEPATH_GITHUB_FILES_NO_WEBHOOK = config.getString("FILEPATH_GITHUB_FILES_NO_WEBHOOK");

        // Threads werden erstellt
        for(int i=0; i<WEBHOOK_FILE_FINDER_AMOUNT; i++){
            webhookCodeFinders.add(new WebhookCodeFinder());
        }
        for(int i=0; i<WEBHOOK_FILE_CHECKER_AMOUNT; i++){
            webhookFileCheckers.add(new WebhookFileChecker());
        }
        oldFileChecker = new OldFileChecker();
        noFileWorker = new NoFileWorker();

        Scanner scanner = new Scanner(System.in);
        String input;
        while (true) {
            input = scanner.nextLine();
            if (input.equalsIgnoreCase("stop") || input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("end")) {
                if(!dontClose)
                    break;
                else
                    System.out.println("[USER] Momentan darf das Programm nicht beendet werden. Bitte schließe nicht dieses Fenster, da so gefundene URLs verloren gehen könnten.");
            }else if (input.equalsIgnoreCase("status")){
                System.out.println("[STATUS] Anzahl Webhook Code FINDER: " + webhookCodeFinders.size());
                System.out.println("[STATUS] Anzahl Webhook Repository CHECKER: " + webhookFileCheckers.size());
                System.out.println("[STATUS] Anzahl Webhook Repository URLS TO CHECK: " + WEBHOOK_FILE_URLS.size());
                System.out.println("[STATUS] Anzahl Webhook URLS FOUND: " + FOUND_WEBHOOK_FILE_URLS.size());

            }else {
                System.out.println("[USER] Gib 'stop', 'exit' oder 'end' ein, um das Programm zu beenden. Gibt 'status' ein für aktuelle Informationen.");
            }
        }


        // Stop Threads
        scanner.close();
        for(WebhookCodeFinder wf : webhookCodeFinders){
            wf.stopThread();
        }
        for(WebhookFileChecker wc : webhookFileCheckers){
            wc.stopThread();
        }
        oldFileChecker.stopThread();
        noFileWorker.stopThread();
    }

    private static JSONObject readConfigFromFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            return new JSONObject(jsonContent.toString());
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

}