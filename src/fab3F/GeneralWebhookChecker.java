package fab3F;

import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class GeneralWebhookChecker {

    public static boolean checkIfThisWorks(String webHookUrl){
        boolean working = false;
        try {
            URL url = new URL(webHookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            working = responseCode == 200;
            connection.disconnect();
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return working;
    }

    public static void addFile(String fileUrl){
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Main.FILEPATH_GITHUB_FILE_WITH_WEBHOOK, true))) {
            writer.write(fileUrl);
            writer.newLine();
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }
    }

    public static void actionWebook(String webHookUrl, String fileUrl){
        String[] parts = fileUrl.split("/");
        String repositoryUrl = "https://github.com/" + parts[3] + "/" + parts[4];
        System.out.println("[HIT] Funktionierender Webhook gefunden: " + fileUrl);


        // Sammeln von Informationen über Webhook und User
        String webhook_username = "";
        String webhook_id = "";
        String webhook_avatarUrl = "";
        String webhook_guildId = "";
        String webhook_channelId = "";
        String user_username = "";
        String user_tag = "";
        String user_id = "";
        String user_avatarUrl = "";
        int user_flags = 0;
        LocalDateTime dateTime = LocalDateTime.parse(new Date().toInstant().toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        LocalDateTime germanyDateTime = dateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of("Europe/Berlin"))
                .toLocalDateTime();
        String date = germanyDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

        try {
            URL url = new URL(webHookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // GET-Anfrage senden
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                // Antwort verarbeiten
                String responseString = response.toString();
                JSONObject webhookJson = new JSONObject(responseString);
                webhook_username = webhookJson.getString("name");
                webhook_id = webhookJson.getString("id");
                webhook_guildId = webhookJson.getString("guild_id");
                webhook_channelId = webhookJson.getString("channel_id");
                try{
                    webhook_avatarUrl = "https://cdn.discordapp.com/avatars/" + webhook_id + "/" + webhookJson.getString("avatar") + ".png";
                }catch (JSONException e){
                    // Dann ist kein avatar ausgewählt
                    webhook_avatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png";
                }
                JSONObject user = webhookJson.getJSONObject("user");
                user_username = user.getString("username");
                user_tag = user.getString("discriminator");
                user_id = user.getString("id");
                try{
                    user_avatarUrl = "https://cdn.discordapp.com/avatars/" + user_id + "/" + user.getString("avatar") + ".png";
                }catch (JSONException e){
                    // Dann ist kein avatar ausgewählt
                    user_avatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png";
                }
                user_flags = user.getInt("flags");
            } else {
                if(Main.debug)
                    System.out.println("[ERROR] Fehler beim Abrufen der Webhook-Informationen in General Webhook Checker. Statuscode: " + responseCode);
            }
            connection.disconnect();
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }


        // Benachichtigung auf EasyFlick
        DiscordWebhook ezWebhook = new DiscordWebhook(Main.LOG_WEBHOOK_URL+Main.LOG_WEBHOOK_TOKEN);
        ezWebhook.setUsername("EZ HOOK LOGS");
        ezWebhook.setContent("Neuen Webhook gefunden!");
        ezWebhook.setAvatarUrl("https://i.ibb.co/dJg3RZS/Easy-Flick-logo.png");
        ezWebhook.setTts(false);
        ezWebhook.addEmbed(new DiscordWebhook.EmbedObject()
                .setTitle(webhook_username)
                .setThumbnail(webhook_avatarUrl)
                .setDescription("Webhook Id: " + webhook_id + "\\n" +
                        "Guild Id: " + webhook_guildId + "\\n" +
                        "Channel Id: " + webhook_channelId + "\\n" +
                        "Repository URL: " + repositoryUrl + "\\n" +
                        "File URL: " + fileUrl + "\\n" +
                        "Webhook URL: " + webHookUrl)
                .setColor(Color.red)
                .setUrl(fileUrl)
                .setFooter(date, "")
        );
        ezWebhook.addEmbed(new DiscordWebhook.EmbedObject()
                .setTitle(user_username)
                .setThumbnail(user_avatarUrl)
                .setDescription("Der Webhook wurde von diesem User erstellt.\\n" +
                        "User Id: " + user_id + "\\n" +
                        "User Tag: #" + user_tag + "\\n" +
                        "User Flags: " + user_flags)
                .setColor(Color.blue)
                .setFooter(date, "")
        );
        try {
            ezWebhook.execute();
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }


        // Benachichtigung auf dem betroffenen Server
        DiscordWebhook webhook = new DiscordWebhook(webHookUrl);
        webhook.setUsername("EasyFlick Bot");
        webhook.setContent("@everyone Found your Webhook!");
        webhook.setAvatarUrl("https://i.ibb.co/dJg3RZS/Easy-Flick-logo.png");
        webhook.setTts(false);
        webhook.addEmbed(new DiscordWebhook.EmbedObject()
                .setTitle("WEBHOOK FOUND")
                .setDescription(("We found your webhook online. Dont worry, you just have to change your webhook url.\\n" +
                        "For more information just check out our discord link:\\n" +
                        "https://dsc.gg/easyflick"))
                .setColor(Color.black)
                .setUrl(fileUrl)
                .setFooter(date, "")
        );
        try {
            webhook.execute();
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }


        // Webhook löschen
        try {
            URL url = new URL(webHookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");

            // DELETE-Anfrage senden
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                if(Main.debug)
                    System.out.println("[INFORMATION] Webhook erfolgreich gelöscht.");
            } else {
                if(Main.debug)
                    System.out.println("[INFORMATION] Fehler beim Löschen des Webhooks. Statuscode: " + responseCode);
            }
            connection.disconnect();
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }



    }




}
