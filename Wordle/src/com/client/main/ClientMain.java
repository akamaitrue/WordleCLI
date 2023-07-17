package com.client.main;
import com.server.main.multicast.Multicast;
import java.io.*;
import java.net.*;
import java.util.*;

public final class ClientMain {

    private ClientMain() {}

    // variabili che conterranno i valori scelti dall'utente, letti dal file config.json
    static String username, password, ip, multicast, guess, response;
    static int port, multicast_port, sockTimeout, max_login_retries;
    
    public static void main(String[] args) throws IOException, InterruptedException {
            
    final int MAX_ATTEMPTS = 12; // numero massimo di tentativi per indovinare la parola

    // leggo dal file config.json le impostazioni desiderate dall'utente
    UtilsClient.parseConfig();

    // inizio la connessione con il server, tutte le operazioni vengono eseguite in un try-with-resources
    try (
        Socket socket = new Socket(ip, port);
        PrintWriter writer = new PrintWriter(socket.getOutputStream());
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Scanner userInput = new Scanner(System.in);
        MulticastSocket s = new MulticastSocket(multicast_port);
        ) {
        // faccio partire un thread che intercetta un eventuale sigint e termina in maniera pulita
        UtilsClient.handleSIGINT(username, writer);
        socket.setSoTimeout(sockTimeout);

        // controllo che la pw soddisfi i requisiti (vedi GameServer.register)
        while (!UtilsClient.register(username, password, writer)) {
            System.out.println("Invalid password, retry: ");
            password = userInput.nextLine();
        }
        response = reader.readLine();
        //System.out.println(response);

        UtilsClient.login(username, password, writer);
        response = reader.readLine();
        //System.out.println(response);

        // se il login fallisce, chiedo all'utente di reinserire la password per un massimo di <max_login_retries> volte preso dal file config.json
        while (response.equalsIgnoreCase("Login failed")) {
            if (max_login_retries == 0) {
                System.out.println("Too many login attempts");
                return;
            }
            System.out.println("Invalid password, retry: ");
            password = userInput.nextLine();
            UtilsClient.login(username, password, writer);
            response = reader.readLine();
            max_login_retries--;
        }

        Thread t = null;
        // se il login va a buon fine, faccio partire un thread che intercetta e gestisce i messaggi multicast
        Multicast.joinGroup(multicast, multicast_port, socket, s, t);
        // inizio il gioco
        while (true) {
            UtilsClient.playWORDLE(writer, username);
            response = reader.readLine();
            //System.out.println(response);
            if (response.equalsIgnoreCase("You already played this word, try again later...")) {
                // scommentare se il comportamento desiderato dal programma è che l'utente si rimetta in attesa di una nuova parola da indovinare (non è specificato nel testo)
                // System.out.println(response);
                // Thread.sleep(10000);
                // continue;
                System.out.println(response);
                return;
            }
            int guesses = 1;
            while (guesses <= MAX_ATTEMPTS) {
                guess = "";
                // se la parola inserita non è di 10 lettere, chiedo all'utente di reinserirla senza incrementare il numero di tentativi
                while (guess.length() != 10) {
                    System.out.println("[" + guesses + "/" + MAX_ATTEMPTS + "] Enter a 10 letter word ('exit' to quit): ");
                    guess = userInput.nextLine();
                    // l'utente ha la possibilità di abbandonare senza dover necessariamente concludere la partita
                    if (guess.equalsIgnoreCase("exit")) {
                        UtilsClient.logout(username, writer);
                        return;
                    }
                }
                // l'utente invia il tentativo (parola) al server
                UtilsClient.sendWord(guess, writer);
                response = reader.readLine();
                System.out.println(response);
                if (response.equalsIgnoreCase("Guess doesn't exist in the file")) {
                    // se la parola provata dall'utente non è presente nel vocabolario, il tentativo non viene conteggiato
                    continue;
                }
                guesses++;
                // se l'utente ha indovinato la parola, la partita termina e il server invia al client le statistiche
                if (response.split(" ")[0].equalsIgnoreCase("You") && response.split(" ")[1].equalsIgnoreCase("won!")) {
                    break;
                }
            }
            // l'utente riceve le sue statistiche dal server
            while (reader.ready()) {
                response = reader.readLine();
                System.out.println(response);
            }

            // l'utente può decidere se condividere il risultato con gli altri utenti tramite un messaggio multicast
            System.out.println("Do you want to share your result? (y/n)");
            String share = userInput.nextLine();
            while (!share.equalsIgnoreCase("y") && !share.equalsIgnoreCase("n")) {
                System.out.println("Invalid input, try again (y/n)");
                share = userInput.nextLine();
            }
            if (share.equalsIgnoreCase("y")) {
                UtilsClient.share(username, writer);
                Thread.sleep(1000);
            }

            // l'utente può decidere se visualizzare la classifica attuale
            System.out.println("Do you want to see the leaderboard? (y/n)");
            String showLeaderboard = userInput.nextLine();
            while (!showLeaderboard.equalsIgnoreCase("y") && !showLeaderboard.equalsIgnoreCase("n")) {
                System.out.println("Invalid input, try again (y/n)");
                showLeaderboard = userInput.nextLine();
            }
            if (showLeaderboard.equalsIgnoreCase("y")) {
                UtilsClient.showMeRanking(writer);
                // sleep per dare il tempo al server di inviare la classifica
                Thread.sleep(1000);
                while (reader.ready()) {
                    response = reader.readLine();
                    System.out.println(response);
                }
            }
            
            // l'utente può scegliere se visulizzare sulla CLI le notifiche inviate dal server riguardo alle partite degli altri utenti
            System.out.println("Do you want to see notifications shared from other users? (y/n)");
            String showNotifications = userInput.nextLine();
            while (!showNotifications.equalsIgnoreCase("y") && !showNotifications.equalsIgnoreCase("n")) {
                System.out.println("Invalid input, try again (y/n)");
                showNotifications = userInput.nextLine();
            }
            if (showNotifications.equalsIgnoreCase("y")) {
                UtilsClient.showMeSharing(username, writer);
                Thread.sleep(1000);
                while (reader.ready()) {
                    response = reader.readLine();
                    System.out.println(response);
                }
            }

            // l'utente può decidere se giocare ancora, mantenendo la stessa sessione
            // in caso affermativo, se la parola da indovinare è ancora la stessa, l'utente attende finché non cambia
            // se l'utente sceglie di non rigiocare si effettua il logout e si chiude la connessione
            System.out.println("Do you want to play again? (y/n)");
            String playAgain = userInput.nextLine();
            while (!playAgain.equalsIgnoreCase("y") && !playAgain.equalsIgnoreCase("n")) {
                System.out.println("Invalid input, try again (y/n)");
                playAgain = userInput.nextLine();
            }
            if (playAgain.equalsIgnoreCase("y")) {
                while (!UtilsClient.getUpdates(username, writer, reader)) {
                    try {
                        Thread.sleep(5000);
                        System.out.println("Sleeping until a new word is available...");
                    }
                    catch (InterruptedException e) {
                        System.out.println("Interrupted while sleeping");
                    }
                }
                // la parola è cambiata, l'utente può giocare una nuova partita
                System.out.println("Starting new game...");
                continue;
            }
            // se il giocatore non vuole giocare più, si disconnette dal server
            UtilsClient.logout(username, writer);
            return;
        }
    }
    }
}   