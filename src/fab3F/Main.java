package fab3F;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Main {

    private static JSONObject config;

    public static boolean debug; // Bei false werden Fehler ignoriert
    public static String GITHUB_TOKEN; // nur 90 Tage gültig Stand 4.6.23
    public static String LOG_WEBHOOK_TOKEN;
    public static String LOG_WEBHOOK_URL;

    public static int RANDOM_REPOSITORY_FINDER_AMOUNT_PER_REQUEST; // MAX = 100
    public static int RANDOM_REPOSITORY_FINDER_AMOUNT_MAX_URLS_IN_LIST; // Maximale Anzahl an Urls, die in der Liste sein sollen. Erst wenn weniger drin sind, werden neue hinzugefügt
    public static int RANDOM_REPOSITORY_FINDER_AMOUNT; // Anzahl an zufälligen Repository Findern
    public static int RANDOM_REPOSITORY_CHEKCER_AMOUNT; // Anzahl an zufälligen Repository Checkern (am besten immer pro finder 10 checker)

    public static int WEBHOOK_REPOSITORY_FINDER_AMOUNT_PER_REQUEST; // Auf hunderter gerundet! MAX = 1000 | Sollte aber nicht größer als 400 sein
    public static int WEBHOOK_REPOSITORY_FINDER_AMOUNT_MAX_URLS_IN_LIST; // Maximale Anzahl an Urls, die in der Liste sein sollen. Erst wenn weniger drin sind, werden neue hinzugefügt
    public static int WEBHOOK_REPOSITORY_FINDER_AMOUNT; // Anzahl an Webhook Repository Findern
    public static int WEBHOOK_REPOSITORY_CHECKER_AMOUNT; // Anzahl an Webhook Repository Checkern (am besten immer pro finder 10 checker)


    public static String FILEPATH_GITHUB_REPOSITORY_WITH_WEBHOOK;
    public static String FILEPATH_GITHUB_REPOSITORYS_NO_WEBHOOK;
    public static String LETTERS_NO_RANDOM_REPOSITORY; // diese buchstaben liefern nur repos von vor einem monat oder so
    public static List<Character> charList; // fertige Liste an Buchstaben für random suche

    public static final Random random = new Random();
    public static final Pattern DISCORD_WEBHOOK_PATTERN = Pattern.compile("https://discord(?:app)?\\.com/api/webhooks/[\\w-]+/[\\w-]+");

    public static List<String> RANDOM_REPOSITORY_URLS = new ArrayList<>(); // Liste mit zufälligen kürzlich aktualisierten Repositorys
    public static List<String> WEBHOOK_REPOSITORY_URLS = new ArrayList<>(); // Liste mit Repository Urls, die das webhook pattern enthalten
    public static List<String> FOUND_WEBHOOK_REPOSITORY_URLS = new ArrayList<>(); // Liste mit Github Repository Urls, die einen funktioniernden Webhook enthalten (temporär)

    public static boolean dontClose = false;

    public static List<RandomRepositoryFinder> randomRepositoryFinders = new ArrayList<>();
    public static List<RandomRepositoryChecker> randomRepositoryCheckers = new ArrayList<>();
    public static List<WebhookCodeFinder> webhookCodeFinders = new ArrayList<>();
    public static List<WebhookRepositoryChecker> webhookRepositoryCheckers = new ArrayList<>();
    public static OldRepositoryChecker oldRepositoryChecker;
    public static NoRepositoryWorker noRepositoryWorker;

    public static void main(String[] args) {
        // Konfiguartion
        config = readConfigFromFile("config.json");
        if (config == null) {
            System.out.println("[ERROR] Fehler beim Lesen der Konfigurationsdatei.");
            return;
        }
        debug = config.getBoolean("debug");
        GITHUB_TOKEN = config.getString("GITHUB_TOKEN");
        LOG_WEBHOOK_TOKEN = config.getString("LOG_WEBHOOK_TOKEN");
        LOG_WEBHOOK_URL = config.getString("LOG_WEBHOOK_URL");
        RANDOM_REPOSITORY_FINDER_AMOUNT_PER_REQUEST = config.getInt("RANDOM_REPOSITORY_FINDER_AMOUNT_PER_REQUEST");
        RANDOM_REPOSITORY_FINDER_AMOUNT_MAX_URLS_IN_LIST = config.getInt("RANDOM_REPOSITORY_FINDER_AMOUNT_MAX_URLS_IN_LIST");
        RANDOM_REPOSITORY_FINDER_AMOUNT = config.getInt("RANDOM_REPOSITORY_FINDER_AMOUNT");
        RANDOM_REPOSITORY_CHEKCER_AMOUNT = config.getInt("RANDOM_REPOSITORY_CHEKCER_AMOUNT");
        WEBHOOK_REPOSITORY_FINDER_AMOUNT_PER_REQUEST = config.getInt("WEBHOOK_REPOSITORY_FINDER_AMOUNT_PER_REQUEST");
        WEBHOOK_REPOSITORY_FINDER_AMOUNT_MAX_URLS_IN_LIST = config.getInt("WEBHOOK_REPOSITORY_FINDER_AMOUNT_MAX_URLS_IN_LIST");
        WEBHOOK_REPOSITORY_FINDER_AMOUNT = config.getInt("WEBHOOK_REPOSITORY_FINDER_AMOUNT");
        WEBHOOK_REPOSITORY_CHECKER_AMOUNT = config.getInt("WEBHOOK_REPOSITORY_CHECKER_AMOUNT");
        FILEPATH_GITHUB_REPOSITORY_WITH_WEBHOOK = config.getString("FILEPATH_GITHUB_REPOSITORY_WITH_WEBHOOK");
        FILEPATH_GITHUB_REPOSITORYS_NO_WEBHOOK = config.getString("FILEPATH_GITHUB_REPOSITORYS_NO_WEBHOOK");
        LETTERS_NO_RANDOM_REPOSITORY = config.getString("LETTERS_NO_RANDOM_REPOSITORY");
        charList = generateCharList(LETTERS_NO_RANDOM_REPOSITORY);

        // Threads werden erstellt
        for(int i=0; i<RANDOM_REPOSITORY_FINDER_AMOUNT; i++){
            randomRepositoryFinders.add(new RandomRepositoryFinder());
        }
        for(int i=0; i<RANDOM_REPOSITORY_CHEKCER_AMOUNT; i++){
            randomRepositoryCheckers.add(new RandomRepositoryChecker());
        }
        for(int i=0; i<WEBHOOK_REPOSITORY_FINDER_AMOUNT; i++){
            webhookCodeFinders.add(new WebhookCodeFinder());
        }
        for(int i=0; i<WEBHOOK_REPOSITORY_CHECKER_AMOUNT; i++){
            webhookRepositoryCheckers.add(new WebhookRepositoryChecker());
        }
        oldRepositoryChecker = new OldRepositoryChecker();
        noRepositoryWorker = new NoRepositoryWorker();

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
                System.out.println("[STATUS] Anzahl Random Repository FINDER: " + randomRepositoryFinders.size());
                System.out.println("[STATUS] Anzahl Random Repository CHECKER: " + randomRepositoryCheckers.size());
                System.out.println("[STATUS] Anzahl Webhook Code FINDER: " + webhookCodeFinders.size());
                System.out.println("[STATUS] Anzahl Webhook Repository CHECKER: " + webhookRepositoryCheckers.size());
                System.out.println("[STATUS] Anzahl Random Repository URLS TO CHECK: " + RANDOM_REPOSITORY_URLS.size());
                System.out.println("[STATUS] Anzahl Webhook Repository URLS TO CHECK: " + WEBHOOK_REPOSITORY_URLS.size());
                System.out.println("[STATUS] Anzahl Webhook URLS FOUND: " + FOUND_WEBHOOK_REPOSITORY_URLS.size());
                for(String s : FOUND_WEBHOOK_REPOSITORY_URLS){
                    System.out.println("[STATUS] FOUND REPOSITORY URL: " + s);
                }

            }else {
                System.out.println("[USER] Gib 'stop', 'exit' oder 'end' ein, um das Programm zu beenden. Gibt 'status' ein für aktuelle Informationen.");
            }
        }


        // Stop Threads
        scanner.close();
        for(RandomRepositoryFinder rf : randomRepositoryFinders){
            rf.stopThread();
        }
        for(RandomRepositoryChecker rc : randomRepositoryCheckers){
            rc.stopThread();
        }
        for(WebhookCodeFinder wf : webhookCodeFinders){
            wf.stopThread();
        }
        for(WebhookRepositoryChecker wc : webhookRepositoryCheckers){
            wc.stopThread();
        }
        oldRepositoryChecker.stopThread();
    }

    public static List<Character> generateCharList(String without) {
        List<Character> charList = new ArrayList<>();
        for (char ch = 'a'; ch <= 'z'; ch++) {
            if(!without.contains(String.valueOf(ch))){
                charList.add(ch);
            }
        }
        return charList;
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