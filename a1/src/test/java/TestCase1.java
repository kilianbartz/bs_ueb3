import de.s4kibart.Config;
import de.s4kibart.Transaction;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestCase1 {

    @Test
    void test() {
        Config cfg = new Config("tank", "/v1");
        Transaction t = new Transaction(cfg, "t1");
        t.write("a.txt", "content_t1");

        Transaction t2 = new Transaction(cfg, "t2");
        t2.write("a.txt", "content_t2");
        assert t2.commit();
        assert !t.commit();
        File file = new File("/tank/v1/a.txt");
        System.out.println(file.exists());
        assert !file.exists();
    }
}
