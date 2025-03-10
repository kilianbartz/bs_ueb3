import de.s4kibart.Config;
import de.s4kibart.Transaction;
import de.s4kibart.TransactionNoBuffering;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestCase1 {

    @Test
    void testImpl1() {
        Config cfg = new Config("tank", "/v1");
        Transaction t = new Transaction(cfg, "t1");
        t.setVerbose(true);
        t.write("a.txt", "content_t1");

        Transaction t2 = new Transaction(cfg, "t2");
        t2.write("a.txt", "content_t2");
        assert t2.commit() < 0;
        assert t.commit() > 0;
        File file = new File("/tank/v1/a.txt");
        System.out.println(file.exists());
        assert file.exists();
    }

    @Test
    void testImpl2() {
        Config cfg = new Config("tank", "/v2");
        TransactionNoBuffering t = new TransactionNoBuffering(cfg, "t1");
        t.write("a.txt", "content_t1");

        TransactionNoBuffering t2 = new TransactionNoBuffering(cfg, "t2");
        t2.write("a.txt", "content_t2");
        assert t2.commit() < 0;
        assert t.commit() > 0;
        File file = new File("/tank/v1/a.txt");
        System.out.println(file.exists());
        assert !file.exists();
    }
}
