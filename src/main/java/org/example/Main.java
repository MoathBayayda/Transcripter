package org.example;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {
        TranscriptFilesGenerator transcriptFilesGenerator =
                new TranscriptFilesGenerator("1dc82aa7e90b494baa464bcff661f0df",
                        "https://api.assemblyai.com/v2",5);
        transcriptFilesGenerator.start();
    }
}