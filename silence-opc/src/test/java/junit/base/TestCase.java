package junit.base;

import com.pnc.component.BaseConfiguration;
import com.pnc.domain.MesuringPoint;
import com.pnc.service.MesuringPointService;
import org.junit.Test;

/**
 * Junit 测试基类，主要是为了加载配置文件
 * 使得继承此类的子类不需要再去加载配置文件
 * @author ShuiQing PM	
 * 2014年9月13日 下午5:22:42
 */
public class TestCase {

    @Test
	public void test () {
        BaseConfiguration baseConfiguration = new BaseConfiguration();
        MesuringPointService mesuringPointService = new MesuringPointService();
        mesuringPointService.fetchReadSyncItems(1);
    }
}
