package com.server.main;
import java.io.*;
import java.net.*;
import com.server.main.multicast.Multicast;


public class ReqHandler implements Runnable {
     
    private final Socket socket;
    private final MulticastSocket s;
    private final FileWriter usersWriter, leaderboardWriter;
    private String currWord, username;


    public ReqHandler(Socket socket, MulticastSocket s, String currWord, FileWriter usersWriter, FileWriter leaderboardWriter) {
        this.socket = socket;
        this.currWord = currWord;
        this.s = s;
        this.usersWriter = usersWriter;
        this.leaderboardWriter = leaderboardWriter;
    }
    
    // routine che gestisce le richieste del client
    @Override
    public void run() {
        try (
            // risorse che serviranno per le operazioni durante la gestione delle richieste
            // try-with-resources per gestire automaticamente la chiusura delle risorse
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream());
        ) {
            while (!ServerMain.gotSIGINT.get()) {
                String message = null;
                while ((message = reader.readLine()) == null) {
                    // se il client ha chiuso la connessione, esco dal ciclo
                    if (this.socket.isClosed()) {
                        return;
                    }
                }
                // metodo che detecta il tipo della richiesta e la gestisce
                handleReq(message, writer, this.leaderboardWriter, this.usersWriter, this.s);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Errore nella gestione della richiesta: " + e.getMessage());
        }
    }

    // al momento della ricezione di una richiesta, l'handler si occupa di identificarne il tipo e di gestirla attraverso un metodo specifico
    private void handleReq(String req, PrintWriter writer, FileWriter leaderboardWriter, FileWriter usersWriter, MulticastSocket s) throws IOException, InterruptedException {
        String reqType = UtilsServer.identifyReq(req);
        switch (reqType) {
            case "[REGISTER]":
                GameServer.register(req, usersWriter, writer);
                break;
            case "[LOGIN]":
                GameServer.login(req, writer);
                username = req.split(" ")[1];
                break;
            case "[PLAYWORDLE]":
                currWord = ServerMain.new_word;
                GameServer.playWordle(req, usersWriter, leaderboardWriter, writer);
                break;
            case "[LOGOUT]":
                GameServer.logout(req);
                break;
            case "[GUESS]":
                UtilsServer.checkGuess(username, currWord, req, leaderboardWriter, writer, s);
                break;
            case "[STATS]":
                GameServer.sendStats(username, writer);
                break;
            case "[SHARE]":
                username = req.split(" ")[1];
                Multicast.sendMulticast(username, currWord, s, ServerMain.udp_address, ServerMain.udp_port);
                break;
            case "[SHOWMESHARING]":
                GameServer.sendShares(req, writer);
                break;
            case "[PULL]":
                GameServer.getUpdates(req, ServerMain.new_word, writer);
                break;
            case "[RANKS]":
                GameServer.sendRanks(writer);
                break;
            default:
                System.out.println("Invalid request: " + req);
                break;
        }
    }
}