package org.earburn.client.irc;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;

public class IRC extends Thread {
    private String server;
    private String appName;
    private int port;
    private String nickname = "TestNick240418";
    private boolean connected;
    private Output output;
    private String ircUser = "TestNick240418";
    private String currentNickname = "TestNick240418";
    private String serverInfo;
    private List<String> channels = new ArrayList<String>();
    private String statusNormal;
    private String statusFail;
    private Input input;
    private List<String> lowPriorityQueue = Collections.synchronizedList(new ArrayList<String>());
    private List<String> highPriorityQueue = Collections.synchronizedList(new ArrayList<String>());
    private Input inputBin;
    private long lastCheck;

    public IRC(String appName,String server, int port) {
        this.server = server;
        this.appName = appName;
        this.port = port;
        this.setName("IRC main: " + this.appName);
    }



    private class Output extends Thread {
        private boolean handleLowPriority = false;
        private boolean running = true;
        private String quitExplanation;
        private BufferedWriter bufferedWriter;

        private Output(IRC irc,BufferedWriter bufferedWriter) {
            this.setName("IRC output " + irc.getName());
            this.bufferedWriter = bufferedWriter;
        }
        @Override
        public void run() {
            while (this.running) {
                while(IRC.this.highPriorityQueue.isEmpty() && (!this.handleLowPriority || IRC.this.lowPriorityQueue.isEmpty())) {
                    if (!this.running) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException iex) {
                        break;
                    }
                }
                String outputMessage = IRC.this.highPriorityQueue.size() > 0 ? IRC.this.highPriorityQueue.remove(0) : null;
                if (IRC.this.lowPriorityQueue.size() > 0 && outputMessage == null && this.handleLowPriority) {
                    outputMessage = IRC.this.lowPriorityQueue.remove(0);
                }

                if (outputMessage != null) {
                    try {
                        this.bufferedWriter.write(outputMessage + "\r\n");
                        this.bufferedWriter.flush();
                    } catch (IOException iox) {
                    }
                }
            }
            try {
                this.bufferedWriter.write("Exiting. Reason: " + this.quitExplanation + "\r\n");
                this.bufferedWriter.flush();
                this.bufferedWriter.close();
            } catch (IOException iox) {
                iox.printStackTrace();
            }
        }

        private void startLowPriority() {
            this.handleLowPriority = true;
        }

        private void terminate(String message) {
            this.quitExplanation = message;
            IRC.this.lowPriorityQueue.clear();
            IRC.this.highPriorityQueue.clear();
            this.running = false;
        }


    }

    private void queue(boolean priority, String message) {
        if (priority) {
            this.highPriorityQueue.add(message);
        } else {
            this.lowPriorityQueue.add(message);
        }
    }

    private void queueFirstPriority(String message) {
        this.highPriorityQueue.add(0,message);
    }

    public static IRC CreateIRC(String ircName, String nickname, String server,int port) {
        IRC irc = new IRC(ircName,server,port);
        irc.setNickname(nickname);
        return irc;
    }

    private void setNickname(String nickname) {
        this.nickname = nickname.trim();
    }

    private void connect() throws IOException {
        this.connected = false;
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(this.server,this.port));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        this.output = new Output(this,bufferedWriter);
        this.output.start();
        this.sendMessage("USER " + this.ircUser + " 8 * :" + this.appName, true);
        this.sendUpdatedNickname(this.nickname);
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            this.decypherResponse(line);    //Handles response, TODO
            //TODO
        }
        if (!this.currentNickname.equals(this.nickname)) {
            this.sendMessage(this.statusFail,true);
            this.sendUpdatedNickname(this.nickname);
        }
        this.sendMessage(this.statusNormal,true);
        for (String channel : this.channels) {
            this.sendMessage("JOIN :" + channel,true);
        }

        this.output.startLowPriority();
        this.input = new Input(bufferedReader,this,socket);
        this.input.start();
        this.connected = true;
    }

    private void sendMessage(String message, boolean isPriority) {
        this.queue(isPriority,message);
    }

    private  void sendUpdatedNickname(String latestNickname) {
        this.sendMessage("NICK " + latestNickname, true);
        this.currentNickname = latestNickname;

    }

    private void decypherResponse(String line) {
        //TODO


    }

    private String getNickname(String nickname) {
        int stripTo = nickname.indexOf("!");
        return nickname.substring(0, stripTo > 0 ? stripTo : nickname.length());
    }

    private String considerColon(String message) {
        return message.startsWith(":") ? message.substring(1) : message;
    }

    public void addChannel(String... channel) {
        this.channels.addAll(Arrays.asList(channel));
    }



    private class Input extends Thread {
        private IRC irc;
        private Socket socket;
        private long lastInput;
        private boolean running = true;
        private BufferedReader bufferedReader;

        private Input(BufferedReader bufferedReader, IRC irc, Socket socket) {
            this.bufferedReader = bufferedReader;
            this.irc = irc;
            this.socket = socket;
            this.setName("IRC input " + irc.getName());
        }

        @Override
        public void run() {
            while (this.running) {
                try {
                    String line = null;
                    while (this.running && ((line = this.bufferedReader.readLine()) != null)) {
                        this.lastInput = System.currentTimeMillis();
                        try {
                            this.irc.decypherResponse(line);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                } catch (IOException iox) {
                    //Handle

                }
            }
            try {
                this.socket.close();
            } catch (IOException iox) {
                iox.printStackTrace();
            }
        }

        private void terminate() {
            this.running = false;
            try {
                bufferedReader.close();
                this.socket.close();
            } catch (IOException iox) {
            }
        }

        private long timeSinceInput() {
            return System.currentTimeMillis() - this.lastInput;
        }
    }

    @Override
    public void run() {
        try {
            this.connect();
        } catch (IOException iox) {
            iox.printStackTrace();
            if (this.inputBin.isAlive() && !this.inputBin.isInterrupted()) {
                this.inputBin.interrupt();
            }
            return;
        }
        while (!Thread.interrupted()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                break;
            }
            if ((System.currentTimeMillis() - this.lastCheck) > 500) {
                this.lastCheck = System.currentTimeMillis();
            }
        }
    }


}
