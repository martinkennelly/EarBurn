import org.earburn.client.irc.IRC;

public class Controller {
    public static void main(String[] args) {
        IRC irc = new IRC("martinkennelly51","localhost",6667);
        irc.start();
    }
}
