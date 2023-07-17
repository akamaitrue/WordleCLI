package com.server.main;
import java.io.*;
import java.util.*;
import java.net.MulticastSocket;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.server.main.multicast.Multicast;

public class GameServer {

    private static JsonArray usersArray = new JsonArray(); // per memorizzare gli utenti registrati
    private static JsonArray leaderboardArray = new JsonArray(); // per memorizzare la classifica
    private static ConcurrentHashMap<String, ArrayList<String>> games = new ConcurrentHashMap<>(); // memorizza le partite attive
    static ConcurrentHashMap<String, JsonObject> usersMap = new ConcurrentHashMap<>(); // memorizza gli utenti registrati
    protected static ConcurrentHashMap<String, String> socialScore = new ConcurrentHashMap<>(); // username e risultato ("censurato") della partita
    static ConcurrentHashMap<String, Integer> sessions = new ConcurrentHashMap<>(); // memorizza gli utenti attualmente loggati
    // make notifications a synchronized arraylist
    protected static Collection<String> notifications = Collections.synchronizedList(new ArrayList<String>()); // per memorizzare le notifiche (thread safe)
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create(); // per stampare il json in modo più leggibile
    final static String GREEN = "\u001B[32m";
    final static String YELLOW = "\u001B[33m";
    final static String RESET = "\u001B[0m";

    // verifica se l'utente è già registrato, controllando se lo username è presente nella hash map users
    private static boolean isRegistered(String username) {
        return usersMap.containsKey(username);
    }
    

    static void register(String req, FileWriter usersWriter, PrintWriter writer) throws IOException{
        String username = req.split(" ")[1];
        String password = req.split(" ")[2];
        
        // controlla che l'utente non sia già registrato
        if (isRegistered(username)) {
            writer.printf("User '%s' already registered\n", username);
            writer.flush();
            return;
        }
        // se l'utente non è già registrato, salva le credenziali nel file users.json
        storeInfo(username, password, usersWriter);
        writer.println("Registration successful");
        writer.flush();
    }


    // memorizza le informazioni dell'utente nel file users.json e nelle strutture dati ausiliarie
    private static void storeInfo(String username, String password, FileWriter usersWriter) throws IOException {
        // se l'utente non è presente nella hash map users lo aggiungo
        JsonObject newUser = new JsonObject();
        newUser.addProperty("username", username);
        newUser.addProperty("password", password);
        usersMap.putIfAbsent(username, newUser);
        
        synchronized(usersArray) {
            synchronized(leaderboardArray) {
                // lo aggiungo anche ai JsonArray
                if (usersArray.size() != 0) {
                    usersArray = gson.fromJson(new JsonReader(new FileReader(UtilsServer.projectDir + "/data/users.json")), JsonArray.class);
                }
                usersArray.add(newUser);
                leaderboardArray.add(newUser);
            }
                // svuoto il file users.json
                PrintWriter pw = new PrintWriter(UtilsServer.projectDir + "/data/users.json");
                pw.close();

                // scrivo il JsonArray sul file users.json
                usersWriter.write(gson.toJson(usersArray));
                usersWriter.flush();
        }
    }
    

    // controlla se user:pass sono corretti tramite la hash map users
    private static boolean checkLogin(String username, String password) {
        return (usersMap.containsKey(username) && usersMap.get(username).get("password").getAsString().equals(password));
    }

    // convalida le credenziali dell'utente e lo fa loggare
    public static void login(String req, PrintWriter writer) throws IOException{
        String username = req.split(" ")[1];
        String password = req.split(" ")[2];

        // controlla che le credenziali siano presenti nel file users.json
        if (checkLogin(username, password)) {
            sessions.put(username, 0);
            writer.println("Welcome back " + username);
            writer.flush();
        }
        else {
            writer.println("Login failed");
            writer.flush();
        }
    }


    // inizia una nuova partita
    static void playWordle(String req, FileWriter usersWriter, FileWriter leaderboardWriter, PrintWriter writer) throws IOException {
        ArrayList<String> playedWords = new ArrayList<String>();
        String username = req.split(" ")[1];
        
        if (games.containsKey(username)) {
            playedWords = games.get(username);            
            // controllo che l'utente non abbia già giocato una partita con quella secret word
            if (playedWords.contains(ServerMain.new_word)) {
                writer.println("You already played this word, try again later...");
                writer.flush();
                return;
            }
            else {
                playedWords.add(ServerMain.new_word);
                games.put(username, playedWords);
            }
        }
        // se l'utente non ha ancora giocato una partita
        else {
            games.put(username, new ArrayList<>());
            playedWords = games.get(username);
            playedWords.add(ServerMain.new_word);
            games.put(username, playedWords);
        }

        JsonObject user = new JsonObject();
        synchronized(usersArray) {
            // leggo gli utenti dal file users.json e li salvo in usersArray, che userò in seguito per aggiornare il file
            usersArray = gson.fromJson(new JsonReader(new FileReader(UtilsServer.projectDir + "/data/users.json")), JsonArray.class);

            // se il file users.json è vuoto
            if (usersArray == null) {
                usersArray = new JsonArray();
                // aggiungo il giocatore corrente con le parole giocate aggiornate
                user.addProperty("username", username);
                user.addProperty("password", usersMap.get(username).get("password").getAsString());
            }
            else {
                // cerco l'utente corrente tra gli utenti registrati
                for (int i = 0; i < usersArray.size(); i++) {
                    user = usersArray.get(i).getAsJsonObject();
                    if (user.get("username").getAsString().equals(username)) {
                        break;
                    }
                }

                usersArray.remove(user);
                // aggiorno la property playedWords aggiungendo la nuova secret word
                if (user.has("playedWords")) {
                    playedWords = gson.fromJson(user.get("playedWords").getAsString(), ArrayList.class);
                    user.remove("playedWords");
                }
                else {
                    playedWords = new ArrayList<>();
                }
            }
            playedWords.add(ServerMain.new_word);
            user.addProperty("playedWords", playedWords.toString());
            usersArray.add(user);

            // svuoto il file users.json
            PrintWriter pw = new PrintWriter(UtilsServer.projectDir + "/data/users.json");
            pw.close();

            // scrivo l'utente con le informazioni aggiornate sul file users.json
            usersWriter.write(gson.toJson(usersArray));
            usersWriter.flush();
        }    
        synchronized(leaderboardArray){
            // aggiorno leaderboardArray
            leaderboardArray = gson.fromJson(new JsonReader(new FileReader(UtilsServer.projectDir + "/data/leaderboard.json")), JsonArray.class);
            if (leaderboardArray == null) leaderboardArray = new JsonArray();
            leaderboardArray.add(user);
        }
        // inizializzo la sessione con numero di tentativi = 0 e inizializzo la stringa socialScore ("risultato no spoiler")
        sessions.put(username, 0);
        socialScore.put(username, "");
        writer.printf("Access granted, good luck %s!\n", username);
        writer.flush();
    }



    // al termine di ogni partita, invia le statistiche dell'utente al client
    static void sendStats(String username, PrintWriter writer) {
        // cerco l'utente nel file leaderboardArray
        // uso leaderboardArray perché contiene le informazioni necessarie per le statistiche
        // dopodiché invio al client l'oggetto relativo all'utente
        JsonObject user = null;
        synchronized(leaderboardArray) {
            for (JsonElement u : leaderboardArray) {
                if (u.getAsJsonObject().get("username").getAsString().equals(username)) {
                    // faccio una deepCopy dell'oggetto per non compromettere le informazioni di leaderboardArray
                    user = u.getAsJsonObject().deepCopy();
                    break;
                }
            }
        }
        if (user != null) {
            // invio solo le statistiche specificate dal testo dell'esercizio e rimuovo quelle non richieste
            user.remove("sconfitte");
            user.remove("indovinate");
            user.remove("avgAttempts");
            user.remove("score");

            String userString = gson.toJson(user);
            writer.println(userString);
            writer.flush();
        }
        else {
            writer.println("Couldn't retrieve stats for " + username);
            writer.flush();
        }
    }


    // invia al client la classifica attuale
    static void sendRanks(PrintWriter writer) {
        // invio solo username e punteggio di ogni utente
        String classificaStringa = "";
        int i = 1;
        synchronized (leaderboardArray) {
            for (JsonElement u : leaderboardArray) {
                JsonObject user = u.getAsJsonObject();
                classificaStringa += i + ". " + user.get("username").getAsString() + " (" + user.get("score").getAsString() + ")\n";
                i++;
            }
        }
        writer.println(classificaStringa);
        writer.flush();
    }



    // invia al client la lista dei risultati no spoiler
    static void sendShares(String req, PrintWriter writer) throws FileNotFoundException {

        String username = req.split(" ")[1];
        // prendo l'ultima parola giocata dall'utente (per la quale ha richiesto di visualizzare le condivisioni degli amici)
        String word = games.get(username).get(games.get(username).size() - 1);
        ArrayList<String> notifsToSend = new ArrayList<>();

        for (String n : notifications) {
            if (n.split(" ")[0].equalsIgnoreCase(word) && !n.split(" ")[1].equalsIgnoreCase(username)) {
                // per readibility manipolo la notifica da inviare, modifico solo word e username e lascio il resto invariato
                String sub0 = n.split(" ")[0] = "word: " + n.split(" ")[0];
                String sub1 = n.split(" ")[1] = " | from: " + n.split(" ")[1];
                // le rimpiazzo in n e mantengo il resto della stringa
                n = sub0 + sub1 + n.substring(n.indexOf(" ", n.indexOf(" ") + 1));
                notifsToSend.add(n);
            }
        }
        if (notifsToSend.size() == 0) {
            writer.printf("Nobody has shared their results for the word '%s'\n", word);
            writer.flush();
            return;
        }
        writer.println(notifsToSend);
        writer.flush();
    }



    // controlla la current word e controlla se l'utente ha già giocato la parola
    static void getUpdates(String req, String currWord, PrintWriter writer) {
        String username = req.split(" ")[1];
        ArrayList<String> playedWords = null;

        if (games.containsKey(username)) {
            playedWords = games.get(username);
            if (playedWords.contains(currWord)) {
                writer.println("NO");
                writer.flush();
                return;
            }
        }
        writer.println("OK");
        writer.flush();
    }


    // chiude la sessione dell'utente
    public static void logout(String req) throws IOException {
        String username = req.split(" ")[1];    
        // rimuovi la sessione
        sessions.remove(username);
        System.out.println(username + " logged out");
    }



    // aggiorna la classifica al termine di ogni partita
    static synchronized void updateLeaderboard(String username, int attempts, boolean won, FileWriter fwriter, MulticastSocket s) throws IOException {
        synchronized(leaderboardArray) {
            leaderboardArray = gson.fromJson(new FileReader(UtilsServer.projectDir + "/data/leaderboard.json"), JsonArray.class);
            String oldFirst = "", oldSecond = "", oldThird = "";
            if (leaderboardArray == null) { leaderboardArray = new JsonArray(); }
            else {
                // la notifica del cambiamento nelle prime tre posizioni è inviata solo se la classifica ha più di 3 utenti
                if (leaderboardArray.size() > 3) {
                    // prendo i primi 3 utenti (quelli con punteggio più basso
                    oldFirst = leaderboardArray.get(0).getAsJsonObject().get("username").getAsString();
                    oldSecond = leaderboardArray.get(1).getAsJsonObject().get("username").getAsString();
                    oldThird = leaderboardArray.get(2).getAsJsonObject().get("username").getAsString();
                }
            }
            JsonObject user = null;

            // se l'utente non ha le proprietà avgAttempts, indovinate e sconfitte (i.e. è la prima partita che disputa), allora gli aggiungo le proprietà
            // altrimenti aggiorno le proprietà
            for (JsonElement u : leaderboardArray) {
                if (u.getAsJsonObject().get("username").getAsString().equals(username)) {
                    user = u.getAsJsonObject();
                    break;
                }
            }
        
            if (user == null) {
                int[] guessDistribution = new int[13];
                for (int i = 0; i < 13; i++) {
                    guessDistribution[i] = 0;
                }
                user = new JsonObject();
                user.addProperty("username", username);
                user.addProperty("avgAttempts", attempts);
                user.addProperty("numPlayed", 1);
                // aggiorno le properties in base al risultato della partita (vittoria o sconfitta)
                if (won) { 
                    user.addProperty("indovinate", 1); 
                    user.addProperty("sconfitte", 0);
                    user.addProperty("winrate", 100);
                    user.addProperty("streak", 1);
                    user.addProperty("maxStreak", 1);
                    guessDistribution[(int) attempts]++;
                    user.addProperty("guessDistribution", Arrays.toString(guessDistribution));
                    user.addProperty("score", UtilsServer.computeScore(user));
                }
                else { 
                    user.addProperty("indovinate", 0); 
                    user.addProperty("sconfitte", 1);
                    user.addProperty("winrate", 0);
                    user.addProperty("streak", 0);
                    user.addProperty("maxStreak", 0);
                    user.addProperty("guessDistribution", Arrays.toString(guessDistribution));
                    user.addProperty("score", UtilsServer.computeScore(user));
                }
                leaderboardArray.add(user);
            }
            else {
                int numPlayed = user.get("numPlayed").getAsInt() + 1;
                user.remove("numPlayed");
                user.addProperty("numPlayed", numPlayed);

                double oldAttempts = user.get("avgAttempts").getAsDouble();
                user.remove("avgAttempts");
                user.addProperty("avgAttempts", (oldAttempts + attempts) / 2);

                int partiteVinte = user.get("indovinate").getAsInt();
                int partitePerse = user.get("sconfitte").getAsInt();
                int streak = user.get("streak").getAsInt();
                int maxStreak = user.get("maxStreak").getAsInt();
                if (won) {
                    partiteVinte++;
                    streak++;
                    if (streak > maxStreak) {
                        maxStreak = streak;
                        user.remove("maxStreak");
                        user.addProperty("maxStreak", maxStreak);
                    }
                    user.remove("streak");
                    user.addProperty("streak", streak);

                    user.remove("indovinate");
                    user.addProperty("indovinate", partiteVinte);

                    JsonElement jsonTree = new JsonParser().parse(user.get("guessDistribution").getAsString());
                    int[] guessDistribution = gson.fromJson(jsonTree, int[].class);
                    guessDistribution[(int) attempts]++;
                    user.remove("guessDistribution");
                    user.addProperty("guessDistribution", Arrays.toString(guessDistribution));
                }
                else {
                    streak = 0;
                    user.remove("streak");
                    user.addProperty("streak", streak);

                    partitePerse++;
                    user.remove("sconfitte");
                    user.addProperty("sconfitte", partitePerse);
                }
                double winrate = (partiteVinte * 100) / (partiteVinte + partitePerse);
                user.remove("winrate");
                user.addProperty("winrate", winrate);

                double score = UtilsServer.computeScore(user);
                user.remove("score");
                user.addProperty("score", score);
            }
            
            // svuoto il file leaderboard.json per riscriverci la nuova classifica
            PrintWriter pw = new PrintWriter(UtilsServer.projectDir + "/data/leaderboard.json");
            pw.close();

            // ordino la classifica in base al punteggio (dal più basso al più alto)
            leaderboardArray = UtilsServer.sortJsonArray(leaderboardArray, "score");

            // scrivo il JsonArray leaderboardArray sul file leaderboard.json
            fwriter.write(gson.toJson(leaderboardArray));
            fwriter.flush();

            // se c'è stato un cambiamento nelle prime 3 posizioni, segnalo il cambiamento a tutti i client connessi al multicast
            if (leaderboardArray.size() > 3) {
                String first = leaderboardArray.get(0).getAsJsonObject().get("username").getAsString();
                String second = leaderboardArray.get(1).getAsJsonObject().get("username").getAsString();
                String third = leaderboardArray.get(2).getAsJsonObject().get("username").getAsString();
                if (!first.equalsIgnoreCase(oldFirst) || !second.equalsIgnoreCase(oldSecond) || !third.equalsIgnoreCase(oldThird)) {
                    String message = "Leaderboard changed: 🥇" + first + " | 🥈" + second + " | 🥉" + third;
                    Multicast.sendMulticast(message, null, s, ServerMain.udp_address, ServerMain.udp_port);
                }
            }
        }
    }

}