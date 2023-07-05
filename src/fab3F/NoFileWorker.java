package fab3F;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NoFileWorker implements Runnable{

    public String PATH;
    public Queue<FileObject> toWork;  // (rawFileUrl, add:true/false)

    private final String name;
    private boolean exitThread;
    Thread thread;

    NoFileWorker() {
        this.name = "NoFileWorker";
        thread = new Thread(this, name);
        System.out.println("[THREAD] Neuer Thread erstellt: " + thread);
        exitThread = false;
        this.PATH = Main.FILEPATH_GITHUB_FILES_NO_WEBHOOK;
        this.toWork = new LinkedList<>();
        thread.start();
    }

    public void stopThread() {
        exitThread = true;
    }

    @Override
    public void run() {
        while (!exitThread){

            if(!Main.noFileWorker.getInstance().toWork.isEmpty()){
                Main.noFileWorker.getInstance().work(Main.noFileWorker.getInstance().toWork.remove());
            }else{
                try {
                    Thread.sleep(1000); // 1 Sekunde Pause machen
                } catch (InterruptedException e) {
                    if(Main.debug)
                        e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
        System.out.println("[THREAD] " + name + " wurde gestoppt.");
    }


    public NoFileWorker getInstance(){
        return this;
    }

    public void work(FileObject fileObject){
        Main.noFileWorker.getInstance().remove(fileObject.getRawFileUrl());
        if(fileObject.isAdd()){
            Main.noFileWorker.getInstance().add(fileObject.getRawFileUrl());
        }

    }

    private void remove(String fileUrl){
        try {
            char letter = fileUrl.toLowerCase(Locale.ROOT).charAt(34);
            File inputFile = new File(PATH, String.valueOf(letter));
            if(!inputFile.exists())
                return;
            File tempFile = new File(letter + "_temp");

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                if (currentLine.contains(fileUrl)) {
                    continue;
                }
                writer.write(currentLine + System.getProperty("line.separator"));
            }

            writer.close();
            reader.close();

            // Lösche die ursprüngliche Datei
            if (inputFile.delete()) {
                // Umbenennen der temporären Datei in den ursprünglichen Dateinamen
                if (!tempFile.renameTo(inputFile)) {
                    if(Main.debug)
                        System.out.println("[ERROR] Fehler im NoFileWorker beim Umbenennen einer temporären Datei.");
                }
            } else {
                if(Main.debug)
                    System.out.println("[ERROR] Fehler im NoFileWorker beim Löschen einer urprünglichen Datei.");
            }
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }
    }

    private void add(String fileUrl){
        // https://github.com/X
        char letter = fileUrl.toLowerCase(Locale.ROOT).charAt(34);
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
            writer.write(fileUrl + "?" + System.currentTimeMillis());
            writer.newLine();
        } catch (IOException e) {
            if(Main.debug)
                e.printStackTrace();
        }

    }

    public boolean haveToCheckThis(String rawFileUrl){
        long date  = getDate(rawFileUrl);
        // Wenn date = 0 dann ist es nicht in der Liste damm haveToCheck, ansonsten Zeit in Millis
        if(date == 0) return true;
        // Wenn Repository geupdated wurde in der Zeit dann haveToCheck
        if(repositoryGotUpdated(rawFileUrl, date)){
            Main.noFileWorker.getInstance().toWork.add(new FileObject(rawFileUrl, false)); // remove aus NoFiles, weil es ja jetzt neu gecheckt wird
            return true;
        }
        else return false;
    }

    public long getDate(String rawFileUrl){
        char letter = rawFileUrl.toLowerCase(Locale.ROOT).charAt(34);
        File file = new File(PATH, String.valueOf(letter));

        if(!file.exists()){
            return 0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if(line.equals(rawFileUrl)){
                    return Long.parseLong(line.replaceAll((rawFileUrl + "?"),""));
                }
            }
        } catch (IOException | NumberFormatException e) {
            if(Main.debug)
                e.printStackTrace();
        }
        return 0;
    }

    public boolean repositoryGotUpdated(String rawFileUrl, long date) {
        try {
            String[] parts = rawFileUrl.split("/");
            String username = parts[3];
            String repoName = parts[4];
            String apiUrl = "https://api.github.com/repos/" + username + "/" + repoName;
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                String response = new String(connection.getInputStream().readAllBytes());
                String lastUpdateStr = response.split("\"pushed_at\":\"")[1].split("Z\"")[0];
                LocalDateTime lastUpdate = LocalDateTime.parse(lastUpdateStr, DateTimeFormatter.ISO_DATE_TIME);
                long lastModified = lastUpdate.toInstant(ZoneOffset.UTC).toEpochMilli();
                System.out.println(lastModified);
                return lastModified > date;
            } else {
                System.out.println("[ERROR] Fehler im NoFileWorker bei der Abfrage der letzen Aktualisierung. Statuscode: ");
            }
        } catch (IOException e) {
            if (Main.debug)
                e.printStackTrace();
        }
        // Lieber überprüfen lassen, wenn ein Fehler aufgetreten ist
        return true;
    }

    public static class FileObject {
        private String f_url;
        private boolean add;

        FileObject(String rawFileUrl, boolean addToNoFiles) {
            this.f_url = rawFileUrl;
            this.add = addToNoFiles;
        }

        public void setRawFileUrl(String rawFileUrl){
            this.f_url = rawFileUrl;
        }
        public void setAdd(boolean addToNoFiles){
            this.add = addToNoFiles;
        }

        public String getRawFileUrl(){
            return this.f_url;
        }
        public boolean isAdd(){
            return add;
        }
    }

}

