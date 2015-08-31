import com.sq.opc.service.MesuringPointService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created with IntelliJ IDEA.
 * User: shuiqing
 * Date: 2015/8/28
 * Time: 11:10
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/shuiqing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class TestSyncPointData extends TestCase {

    @Autowired
    public MesuringPointService mesuringPointService;

    @Test
    public void testSyncData() {
        mesuringPointService.startReceiveSenderService();
    }
}
