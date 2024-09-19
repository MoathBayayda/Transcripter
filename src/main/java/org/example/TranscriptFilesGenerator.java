package org.example;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class TranscriptFilesGenerator {
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    private String token;
    private String baseUrl;
    private OkHttpClient client;

    private int filesCount;
    private Runnable mainOperations;
    public TranscriptFilesGenerator(String token,String baseUrl,int filesCount){
        setToken(token);
        setClient(new OkHttpClient.Builder()
                .callTimeout(5, TimeUnit.MINUTES)
                .connectTimeout(5, TimeUnit.MINUTES)
                .build());
        setBaseUrl(baseUrl);
        setFilesCount(filesCount);
        mainOperations = new Runnable() {
            @Override
            public void run() {
                String path = "src/videos";
                for(int i = 1;i <= 5;i++){
                    System.out.println(ANSI_GREEN+"FILE NUMBER : "+i+ANSI_YELLOW);
                    byte[] data = readMp4(path+"/"+i+".m4a");
                    System.out.println(data);
                    String audio_url = POST_Upload(data);
                    System.out.println(audio_url);
                    String id = POST_Transcript(audio_url);
                    System.out.println(id);
                    while (true){
                        try {
                            Thread.currentThread().sleep(2000);
                        }catch (InterruptedException interruptedException){
                            System.out.println("ERROR WHILE SLEEPING "+Thread.currentThread().getName()+
                                    " "+interruptedException);
                        }
                        String transcriptResponse = GET_Transcript(id);
                        JSONObject transcriptResponseJson = new JSONObject(transcriptResponse);
                        System.out.println(ANSI_GREEN+"TRANSCRIPTION STATUS : "
                                +transcriptResponseJson.getString("status")+ANSI_YELLOW);
                        if (transcriptResponseJson.getString("status").equals("completed")){
                            JSONArray words = new JSONArray(transcriptResponseJson.getJSONArray("words"));
                            String text = GET_Transcript_srt(id);
                            File file = new File("src/transcripts_english/"+i+".srt");
                            File manul = new File("src/manual/"+i+".txt");

                            try{
                                FileOutputStream manulFile=
                                        new FileOutputStream(manul);
                                BufferedOutputStream manulFileStream =
                                        new BufferedOutputStream(manulFile);
                                manulFileStream.write(transcriptResponseJson
                                        .getString("text").getBytes());
                                manulFileStream.flush();
                                FileOutputStream fileOutputStream = new FileOutputStream(file);

                                BufferedOutputStream bufferedOutputStream =
                                        new BufferedOutputStream(fileOutputStream);

                                bufferedOutputStream.write(text.getBytes());
                                bufferedOutputStream.flush();
                                for(int word_i = 0;word_i< words.length();word_i++){
                                    JSONObject word = words.getJSONObject(word_i);
                                    if(word.getDouble("confidence") < 0.7){
                                        bufferedOutputStream.write((word.getString("text")+"--" +
                                                " time :+"+word.getInt("start")/1000.0+"\n"+
                                                "conf : "+ word.getDouble("confidence")+"\n")
                                                .getBytes());
                                    }
                                }
                                bufferedOutputStream.flush();
                            }catch (FileNotFoundException fileNotFoundException){
                                System.out.println("ERROR WHILE OPENING  TEXT FILE NOT FOUND "
                                        +fileNotFoundException+" "+file.getPath()
                                        +" "+Thread.currentThread().getName());
                            }
                            catch (IOException ioException){
                                System.out.println("ERROR WHILE WRITING ON THE FILE "+
                                        " "+ioException+" "+Thread.currentThread().getName());
                            }
                            //todo add the completed english transcribed file to a global paths
                            System.out.println(ANSI_GREEN+
                                    "************************************************"+ANSI_YELLOW);
                            break;
                        }
                    }
                }
            }
        };
    }
    public void start(){
        Thread transcriptionThread = new Thread(mainOperations);
        transcriptionThread.setName("transcription-Thread");
        transcriptionThread.start();
    }
    //returns video bytes
    public byte[] readMp4(String path){
        File file = new File(path);
        byte[] data = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int read;
            //possible error , remove bufferedInputStream object
            //BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
            while ((read = fileInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0,read);
            }
            data = byteArrayOutputStream.toByteArray();
        }catch (FileNotFoundException fileNotFoundException){
            System.out.println("CAN'T FIND FILE "+path+fileNotFoundException
                    +" "+Thread.currentThread().getName());
        }
        catch (IOException ioException){
            System.out.println("ERROR WHILE READING FROM FILE "+path+" "+ioException
                    +" "+Thread.currentThread().getName());
        }
        return data;
    }
    //returns audio_url
    public String POST_Upload(byte[] data){
        Request  request = new Request.Builder()
                .url(getBaseUrl()+"/upload")
                .addHeader("authorization","Bearer "+getToken())
                .addHeader("Content-Type","application/octet-stream")
                .post( RequestBody.create(
                        MediaType.parse("application/octet-stream"), data))
                .build();
       try {
           Response response = getClient().newCall(request).execute();
           return new JSONObject(response.body().string()).getString("upload_url");
       }catch (IOException ioException){
           System.out.println("ERROR IN REQUESTING : "+request.url()+ioException
                   +" "+Thread.currentThread().getName());
       }
       return "ERROR";
    }
    //returns uploaded video transcript id
    public String POST_Transcript(String audioUrl){
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        JSONObject requestBodyJson = new JSONObject();
        requestBodyJson.put("audio_url", audioUrl);
        RequestBody formBody = RequestBody.create(requestBodyJson.toString(), mediaType);
        Request  request = new Request.Builder()
                .url(getBaseUrl()+"/transcript")
                .addHeader("authorization","Bearer "+getToken())
                .addHeader("Content-Type","application/json")
                .post(formBody)
                .build();
        try {
            Response response = getClient().newCall(request).execute();
            return new JSONObject(response.body().string()).getString("id");
        }catch (IOException ioException){
            System.out.println("ERROR IN REQUESTING : "+request.url()+ioException
                    +" "+Thread.currentThread().getName());
        }
        return "ERROR";
    }
    //returns the object that contains the transcript
    public String GET_Transcript(String id){
        Request  request = new Request.Builder()
                .url(getBaseUrl()+"/transcript/"+id)
                .addHeader("authorization","Bearer "+getToken())
                .addHeader("Content-Type","application/json")
                .get()
                .build();
        try {
            Response response = getClient().newCall(request).execute();
            return response.body().string();
        }catch (IOException ioException){
            System.out.println("ERROR IN REQUESTING : "+request.url()+ioException
                    +" "+Thread.currentThread().getName());
        }
        return "ERROR";
    }
    public String GET_Transcript_srt(String id){
        Request  request = new Request.Builder()
                .url(getBaseUrl()+"/transcript/"+id+"/srt")
                .addHeader("authorization","Bearer "+getToken())
                .addHeader("Content-Type","application/json")
                .get()
                .build();
        try {
            Response response = getClient().newCall(request).execute();
            return response.body().string();
        }catch (IOException ioException){
            System.out.println("ERROR IN REQUESTING : "+request.url()+ioException
                    +" "+Thread.currentThread().getName());
        }
        return "ERROR";
    }
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public OkHttpClient getClient() {
        return client;
    }

    public void setClient(OkHttpClient client) {
        this.client = client;
    }

    public int getFilesCount() {
        return filesCount;
    }

    public void setFilesCount(int filesCount) {
        this.filesCount = filesCount;
    }
}
