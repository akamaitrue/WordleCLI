package com.server.main;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerMain {

    static volatile AtomicBoolean gotSIGINT = new AtomicBoolean(false);
    
    // variabili di configurazione, i cui valori vengono letti dal file config.json
    static long new_word_timeout;
    static int port, udp_port, nThreads;
    static String new_word, udp_address;
    
    public static void main(String[] args) throws IOException {
        
        UtilsServer.setConfig(UtilsServer.projectDir + "/settings/server_settings.json"); // legge il file di configurazione e setta i valori
        UtilsServer.fillWordsMap(); // copia il contenuto di words.txt in una mappa per poter accedere alle parole in tempo costante
        UtilsServer.fillUsersMap(); // dopo un riavvio del server, ricopia gli utenti salvati in users.json in una mappa per agevolare i prossimi accessi
        new_word = UtilsServer.pickWord(); // sceglie dal vocabolario una parola casuale che gli utenti dovranno indovinare 

        // timer che ogni <new_word_timeout> secondi aggiorna la parola da indovinare
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    new_word = UtilsServer.pickWord();
                    System.out.println("New word: " + new_word);
                } catch (IOException e) {
                    System.err.println("Errore nella lettura di words.txt");
                }
            }
        }, new_word_timeout*1000, new_word_timeout*1000);

        // avvia il server con try-with-resources
        try ( ServerSocket listener = new ServerSocket(port); 
              MulticastSocket s = new MulticastSocket(udp_port); 
              ExecutorService pool = Executors.newFixedThreadPool(nThreads);
              FileWriter usersWriter = new FileWriter(UtilsServer.projectDir + "/data/users.json", true);
              FileWriter leaderboardWriter = new FileWriter(UtilsServer.projectDir + "/data/leaderboard.json", true);
            ){
            // per la dimensione del pool di thread, ho deciso di usare il doppio del numero di core del processore
            // fixed-size thread pool per evitare che i thread vengano creati e distrutti continuamente
            
            // lancio un thread che si mette in attesa di un eventuale segnale SIGINT per intercettarlo e terminare in maniera pulita
            UtilsServer.handleSIGINT(s, pool);
            System.out.println("Initial word: " + new_word);
            // il server si mette in ascolto di richieste di connessione
            while (!gotSIGINT.get()) {
                String currentWord = new_word;
                Socket client = listener.accept();
                // inoltra il task ad un handler che si occupa di gestire la richiesta
                pool.execute(new ReqHandler(client, s, currentWord, usersWriter, leaderboardWriter));
            }
        } catch (BindException e) {
            System.err.println("Porta " + port + " già occupata");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Errore I/O: " + e.getMessage());
        }
    }
}