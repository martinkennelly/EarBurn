package org.earburn.client.irc;
import org.earburn.client.library.util.StringUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
/**
 * Internet Relay Chat client
 * Connects to IRC server
 * Currently tested locally with UnrealIRCd v4.0.17
 * Help from IRC Spec (@ https://tools.ietf.org/html/rfc2812) and UChicago IRC description (@ http://chi.cs.uchicago.edu/chirc/irc_examples.html)
 *
 * @author  Martin Kennelly
 * @version 0.1
 * @since   20/02/2018
 */

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

    /**
     * This constructed takes in the basic elements needed to form a connection with an Internet
     * relay server.     *
     * @param appName This is the name given to this instance
     * @param server Server hostname address either IP or FQDN
     * @param port Port number to connect to IRC (6667 for standard messages)
     */
    public IRC(String appName,String server, int port) {
        this.server = server;
        this.appName = appName;
        this.port = port;
        this.setName("IRC main: " + this.appName);
    }

    /**
     * Class to provide output focused functionality
     * Extends Thread
     */
    private class Output extends Thread {
        private boolean handleLowPriority = false;
        private boolean running = true;
        private String quitExplanation;
        private BufferedWriter bufferedWriter;

        /**
         * This constructor sets up the output class
         * @param irc
         * @param bufferedWriter
         */
        private Output(IRC irc,BufferedWriter bufferedWriter) {
            this.setName("IRC output " + irc.getName());
            this.bufferedWriter = bufferedWriter;
        }

        /**
         * Method is called when Thread is started and continues operating as long as boolean running is true or
         * an interupt is called.
         * @param          *
         * @return Nothing
         */
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

        /**
         * Method is called when we are ready to handle low priority message
         */
        private void startLowPriority() {
            this.handleLowPriority = true;
        }

        /**
         * Terminate the client.
         * @param message Message to explain why we are terminating
         */
        private void terminate(String message) {
            this.quitExplanation = message;
            IRC.this.lowPriorityQueue.clear();
            IRC.this.highPriorityQueue.clear();
            this.running = false;
        }


    }

    /**
     * Method provides access to queues. If parameter priority is true, then message is high priority else low priority.
     * @param priority Indicated the priority of the message, true for high and false for low priority.
     * @param message Message to be added to the queue
     */
    private void queue(boolean priority, String message) {
        if (priority) {
            this.highPriorityQueue.add(message);
        } else {
            this.lowPriorityQueue.add(message);
        }
    }

    /**
     * Pushes message to the top of the high priority queue
     * @param message Message to be added to que
     */
    private void queueFirstPriority(String message) {
        this.highPriorityQueue.add(0,message);
    }

    /**
     * Creates an instance of IRC
     * @param ircName Name given to instance of IRC
     * @param nickname Nickname given to IRC user
     * @param server IRC server address either IP or FQDN
     * @param port Port to connect to (6667 for standard messages)
     * @return An instance of IRC
     */
    public static IRC CreateIRC(String ircName, String nickname, String server,int port) {
        IRC irc = new IRC(ircName,server,port);
        irc.setNickname(nickname);
        return irc;
    }

    /**
     * Assigns a nickname
     * @param nickname Nickname to be assigned to user
     */
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
            String[] lineSplit = line.split(" ");
            if (lineSplit.length > 3) {
                String code = lineSplit[1];
                if (code.equals("004")) {
                    break;                                                  //Done
                } else if (code.startsWith("4") || code.startsWith("5")) {
                    socket.close();
                    throw new RuntimeException("Failed to connect to server with the following reply:\t" + line);
                }
            }

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
        if (line == null || line.length() == 0) {
            return;
        } else if (line.startsWith("PING ")) {
            this.queueFirstPriority("PONG " + line.substring(5));
            return;
        }
        String lineSplit[] = line.split(" ");
        if (!lineSplit[0].startsWith(":") || lineSplit.length < 2) {
            return;
        }
        String messageSender = lineSplit[0].substring(1);
        if (this.serverInfo == null || messageSender.equalsIgnoreCase(this.serverInfo)) {
            switch (lineSplit[1]) {
                //taken from rpc protocol (pg 50)
                case "NOTICE":
                case "001":
                case "002":
                    System.out.println(StringUtil.combine(lineSplit,3));
                    break;
                case "003":
                    break;
                case "004":
                    this.serverInfo = lineSplit[0].substring(1);
                    break;
                case "005":
                case "250":
                case "251":
                case "252":
                case "253":
                case "254":
                case "255":
                case "265":
                case "266":
                case "372":
                case "375":
                case "376":
                    break;
                case "332":
                case "333":
                case "353":
                case "366":
                case "422":
                    break;
                case "433":     //Nick in use
                    if (!this.connected) {
                        this.sendUpdatedNickname(this.currentNickname + '~');
                    }
                    break;
                default:
                    System.out.println("Unknown string from server: " + line);
            }
        } else {
            //TODO Messaging between users

        }









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
