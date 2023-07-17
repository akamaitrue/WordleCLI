package com.server.main;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.*;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.server.main.multicast.Multicast;

public final class UtilsServer {

    // costruttore privato perché è una utility class e non deve essere istanziata
    private UtilsServer() {}

    // hash map per memorizzare tutte le parole del vocabolario, verrà riempita una sola volta all'avvio del server e usata ogni volta che un giocatore invia un guess
    private static HashMap<String,String> wordsMap = new HashMap<String, String>();
    public static String projectDir = System.getProperty("user.dir");
    
    // leggo il file words.txt e memorizzo tutte le parole del vocabolario per agevolare il server con tutte le ricerche successive (quando deve verificare se una parola inviata dal client è presente nel vocabolario)
    public static void fillWordsMap() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(projectDir + "/data/words.txt"));
            String line;
            while ((line = br.readLine()) != null) {
                wordsMap.put(line, line);
            }
            br.close();
        } catch (IOException e) {
            System.err.println("Error while reading words.txt");
        }
    }

    
    // hash map per memorizzare tutti gli utenti registrati, viene riempita solo dopo un riavvio del server ed è usata ogni volta che un giocatore invia un guess. Viene riempita con i JsonObject letti dal file users.json
    public static void fillUsersMap() throws IOException {
        // controllo se il file è vuoto leggendo il file in una stringa e verificando se è vuota
        String jsonString = new String(Files.readAllBytes(Paths.get(projectDir + "/data/users.json")));
        if (jsonString.isEmpty()) return;
        JsonArray usersArray = new JsonParser().parse(jsonString).getAsJsonArray();
        for (int i = 0; i < usersArray.size(); i++) {
            JsonObject user = usersArray.get(i).getAsJsonObject();
            GameServer.usersMap.put(user.get("username").getAsString(), user);
        }
    }

    // identifica la richiesta inviata dal client in base al prefisso del messaggio 
    // (vedi descrizione del protocollo nella relazione)
    public static String identifyReq (String req) {
        String[] reqArray = req.split(" ");
        return reqArray[0];
    }


    // ordina un JsonArray in base al campo specificato nell'argomento sortBy
    public static JsonArray sortJsonArray (JsonArray myJsonArr, String sortBy) {
    JsonArray sortedArray = new JsonArray();
    ArrayList<JsonObject> objList = new ArrayList<>();
    for (int i = 0; i < myJsonArr.size(); i++) {
        objList.add((JsonObject) myJsonArr.get(i));
    }
    if (sortBy.equals("timestamp")) {
        Collections.sort(objList,
        (o1, o2) -> o1.get(sortBy).getAsString().compareToIgnoreCase(o2.get(sortBy).getAsString()));
    }
    else {
        // sorting in ordine crescente perché per il gioco vale "lower is better", ovvero punteggio più basso=giocatore "più bravo"
        Collections.sort(objList,
        (o1, o2) -> o1.get(sortBy).getAsDouble() > o2.get(sortBy).getAsDouble() ? 1 : -1);
    }
    for (int i = 0; i < myJsonArr.size(); i++) {
        sortedArray.add(objList.get(i));
    }
    return sortedArray;
    }



    // calcola il punteggio di un utente. l'array è zero-based, quindi la posizione 0 è sempre 0
    public static double computeScore(JsonObject user) {
        
        JsonParser parser = new JsonParser();
        // rimuovo \n da user.get("guessDistribution").getAsString()
        JsonElement jsonTree = parser.parse(user.get("guessDistribution").getAsString().stripIndent());
        int[] guessDistributionInt = new int[13];
        for (int i = 0; i < 13; i++) {
            guessDistributionInt[i] = jsonTree.getAsJsonArray().get(i).getAsInt();
        }

        double score = 0;
        int indovinate = 0, maxAttempts = 12, numPlayed = user.get("numPlayed").getAsInt();

        for (int i = 1; i < 13; i++) {
            // posso skippare la posizione 0 perché è sempre 0
            score += i * guessDistributionInt[i];
            indovinate += guessDistributionInt[i];
        }
        // Per le parole non indovinate (partite perse), 
        // considero un numero di tentativi pari a 13 (ovvero maxAttempts + 1).
        score += (maxAttempts + 1) * (numPlayed - indovinate);
        score = score / (double) numPlayed;
        // restituisco il punteggio troncato alla seconda cifra decimale
        return Math.round(score * 100.0) / 100.0;
    }



    // routine per la gestione del segnale SIGINT
    public static void handleSIGINT (MulticastSocket s, ExecutorService pool) {
        // shutdown hook per gestire cmd+c
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println(" Received SIGINT, exiting gracefully...");
                // chiudi risorse
                try {
                    ServerMain.gotSIGINT.set(true);
                    Multicast.sendMulticast("Server shutdown", null, s, ServerMain.udp_address, ServerMain.udp_port);
                    // logout di tutti gli utenti
                    for (String username : GameServer.sessions.keySet()) {
                        GameServer.logout("LOGOUT " + username);
                    }
                    // chiudo socket multicast
                    s.close();
                    // smetto di accettare nuovi task e aspetto che tutti i task in esecuzione terminino
                    // se non terminano entro 5 secondi, l'esecuzione del pool viene interrotta immediatamente
                    pool.shutdown();
                    try {
                        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) 
                            pool.shutdownNow();
                    }
                    catch (InterruptedException e) {pool.shutdownNow();}
                }
                catch (IOException e) {
                    System.err.println("Error while closing resources: " + e.getMessage());
                }
            }
        });
    }



    // leggee le impostazioni dal file di configurazione server_settings.json e le salva 
    public static void setConfig(String filepath) {
        // path: ./settings/server_settings.json
        try (JsonReader jsonReader = new JsonReader(new FileReader(filepath))) {
            jsonReader.beginObject();
            
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                switch (name) {
                    case "next_word":
                        long timeout = jsonReader.nextInt();
                        ServerMain.new_word_timeout = timeout;
                        break;
                    case "pool_size":
                        int pool_size = jsonReader.nextInt();
                        ServerMain.nThreads = pool_size;
                        break;
                    case "port":
                        int port = jsonReader.nextInt();
                        ServerMain.port = port;
                        break;
                    case "multicast_address":
                        String udp_address = jsonReader.nextString();
                        ServerMain.udp_address = udp_address;
                        break;
                    case "multicast_port":
                        int udp_port = jsonReader.nextInt();
                        ServerMain.udp_port = udp_port;
                        break;
                    default:
                    jsonReader.skipValue();
                    break;
                }
            }
            jsonReader.endObject();
            jsonReader.close();
        }
        catch (IOException e) {
            System.err.println("Error reading server_settings.json: " + e.getMessage());
        }
    }
    



    // sceglie una parola casuale dalla map wordsMap
    public static String pickWord() throws IOException {
        Random rand = new Random();
        int randomIndex = rand.nextInt(wordsMap.size());
        String word = (String) wordsMap.keySet().toArray()[randomIndex];
        return word;
    }


    // elabora la guess word ricevuta da un giocatore e invia come risposta i suggerimenti sulle lettere corrette
    public static void checkGuess(String username, String word, String req, FileWriter leaderboardWriter, PrintWriter writer, MulticastSocket s) throws IOException, InterruptedException {
        boolean won = false;
        String result = "";
        String guess = req.split(" ")[1];
        guess = guess.toLowerCase();
        word = word.toLowerCase();
        boolean isGuessInFile = false;
        
        // controlla (in O(1) ) se la stringa guess è presente tra le parole del vocabolario, tramite la hash map wordsMap
        if (wordsMap.containsKey(guess)) {
            isGuessInFile = true;
        }

        // se la stringa guess non è presente nel file, invia un messaggio di errore
        if (!isGuessInFile) {
            writer.println("Guess doesn't exist in the file");
            writer.flush();
            return;
        }
        // cerca lo username nella map sessions e incrementa di 1 il numero di tentativi
        int attempts = GameServer.sessions.get(username) + 1;
        GameServer.sessions.put(username, attempts);
        
        // per ogni carattere della parola controlla se isValid
        if (word.equalsIgnoreCase(guess)) {
            won = true;
            result = "You won! " + GameServer.GREEN + guess + GameServer.RESET;
            GameServer.updateLeaderboard(username, attempts, won, leaderboardWriter, s);
            
            String ITAword = UtilsServer.getTranslation(word).toLowerCase();
            result += " --> " + ITAword;

            String social = GameServer.socialScore.get(username);
            if (social == null) social = "";
            social += "🟩 🟩 🟩 🟩 🟩 🟩 🟩 🟩 🟩 🟩\n";
            GameServer.socialScore.put(username, social);

            writer.println(result);
            writer.flush();
            GameServer.sendStats(username, writer);
            GameServer.sessions.put(username, 0);
            social = "";
            return;
        }
        else {
            // se il numero di tentativi è < 12
            String social = GameServer.socialScore.get(username);
            if (GameServer.sessions.get(username) < 12) {
                if (social == null) social = "";
                for (int i = 0; i < guess.length(); i++) {
                    if (word.contains(guess.charAt(i) + "")) {
                        // se è nella stessa posizione
                        if (word.indexOf(guess.charAt(i)) == i) {
                            result += GameServer.GREEN + guess.charAt(i) + GameServer.RESET;
                            social += "🟩 ";
                        } else {
                            result += GameServer.YELLOW + guess.charAt(i) + GameServer.RESET;
                            social += "🟨 ";
                        }
                    } else {
                        result += guess.charAt(i);
                        social += "⬛ ";
                    }
                }
                social += "\n";
                GameServer.socialScore.put(username, social);               
            }
            else {
                for (int i = 0; i < guess.length(); i++) {
                    if (word.contains(guess.charAt(i) + "")) {
                        // se è nella stessa posizione
                        if (word.indexOf(guess.charAt(i)) == i) {
                            social += "🟩 ";               
                        } else {
                            social += "🟨 ";
                        }
                    } else {
                        social += "⬛ ";
                    }
                }
                social += "\n";
                GameServer.socialScore.put(username, social);
                result = "You lost! The word was " + word;
                String ITAword = UtilsServer.getTranslation(word).toLowerCase();
                result += " (" + ITAword + ")";
                GameServer.updateLeaderboard(username, attempts, won, leaderboardWriter, s);
                writer.println(result);
                writer.flush();
                GameServer.sendStats(username, writer);
                GameServer.sessions.put(username, 0);
                social = "";
                return;
            }
        }
        writer.println(result);
        writer.flush();
    }


    // GET request alla API di mymemory per ottenere la traduzione della parola in italiano
    public static String getTranslation(String word) throws IOException, InterruptedException {
        String url = "https://mymemory.translated.net/api/get?q=" + word + "&langpair=en%7cit"; // build query url
        // l'operatore pipe "|" è un carattere speciale che da problemi al momento di buildare l'URI quindi l' ho codificato in ASCII-> %7c
        // send http get request to url and retrieve translation from response
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseString = response.body();
        JsonObject responseJson = GameServer.gson.fromJson(responseString, JsonObject.class);
        String translation = responseJson.get("responseData").getAsJsonObject().get("translatedText").getAsString();
        return translation;
    }
}