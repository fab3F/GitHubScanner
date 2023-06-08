package fab3F;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class NoRepositoryWorker {

    public String PATH;

    NoRepositoryWorker() {
        this.PATH = Main.FILEPATH_GITHUB_REPOSITORYS_NO_WEBHOOK;
    }

    public NoRepositoryWorker getInstance(){
        return this;
    }

    public void add(String repositoryUrl){
        // https://github.com/X
        char letter = repositoryUrl.charAt(19);
        File file = new File(PATH, String.valueOf(letter));
        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                if(Main.debug)
                    e.printStackTrace();
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(repositoryUrl + "?" + System.currentTimeMillis());
            writer.newLine();
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }

    }

    public boolean haveToCheckThis(String repositoryUrl){
        long date  = getDate(repositoryUrl);
        // Wenn date = 0 dann ist es nicht in der Liste, ansonsten Zeit in Millis
        if(date == 0) return true;
        // Wenn Repository geupdated wurde in der Zeit dann haveToCheck
        else return repositoryGotUpdated(repositoryUrl, date);
    }

    public long getDate(String repositoryUrl){
        char letter = repositoryUrl.charAt(19);
        File file = new File(PATH, String.valueOf(letter));

        if(!file.exists()){
            return 0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if(line.equals(repositoryUrl)){
                    return Long.parseLong(line.replaceAll((repositoryUrl + "?"),""));
                }
            }
        } catch (IOException | NumberFormatException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return 0;
    }

    public boolean repositoryGotUpdated(String repositoryUrl, long date){
        try {
            URL url = new URL(repositoryUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return connection.getLastModified() > date;
            } else {
                if(Main.debug)
                    System.out.println("[ERROR] Fehler im NoRepositoryWorker bei der Abfrage der letzen Aktualisierung. Statuscode: " + responseCode);
            }
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        // Lieber überprüfen lassen, wenn ein Fehler auftreten sollte
        return true;
    }



}
