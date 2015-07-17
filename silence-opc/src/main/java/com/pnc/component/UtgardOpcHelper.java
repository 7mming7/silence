package com.pnc.component;

import com.pnc.excel.handler.ExcelImportHandler;
import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.core.JIVariant;
import org.openscada.opc.dcom.da.OPCSERVERSTATUS;
import org.openscada.opc.dcom.list.ClassDetails;
import org.openscada.opc.lib.common.AlreadyConnectedException;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.AutoReconnectController;
import org.openscada.opc.lib.da.Item;
import org.openscada.opc.lib.da.Server;
import org.openscada.opc.lib.list.Categories;
import org.openscada.opc.lib.list.Category;
import org.openscada.opc.lib.list.ServerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * opc连接辅助类.
 * User: shuiqing
 * Date: 2015/3/30
 * Time: 11:09
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class UtgardOpcHelper {

    private static final Logger log = LoggerFactory.getLogger(UtgardOpcHelper.class);

    private static String filePath = "d:/test.csv";

    /**
     * 连接目标host的opc server
     * @return 服务连接
     */
    public static Server connect(int sid) {

        Server server = new Server(
                BaseConfiguration.getCLSIDConnectionInfomation(sid),
                Executors.newSingleThreadScheduledExecutor());
        server.setDefaultLocaleID(sid);
        AutoReconnectController controller = new AutoReconnectController(server);
        controller.connect();
        return server;
    }

    /**
     * 断开与目标host地址上opc server的连接
     */
    public void closeConnection(Server server) {
        server.dispose();
    }

    /**
     * 获取目标地址下所有的opc服务的详细信息
     * @return
     * @throws org.jinterop.dcom.common.JIException
     * @throws java.net.UnknownHostException
     */
    public Collection<ClassDetails> fetchClassDetails (int sid) {
        Collection<ClassDetails> classDetails = null;

        try {
            ConnectionInformation connectionInformation = OpcRegisterFactory.fetchConnInfo(sid);
            ServerList serverList = new ServerList(connectionInformation.getHost(),
                    connectionInformation.getUser(), connectionInformation.getPassword(),
                    connectionInformation.getDomain());

            /** According the progid get the clsid, then get the classdetail */
            /** Whatever the using DA agreement */
            classDetails = serverList
                    .listServersWithDetails(new Category[] {
                            Categories.OPCDAServer10, Categories.OPCDAServer20,
                            Categories.OPCDAServer30 }, new Category[] {});

            log.info("-----------------------------------------------------------");
            log.info("--------开始获取目标Ip：" + connectionInformation.getHost() + "下所有on service的opc服务.-----");
            for (ClassDetails cds : classDetails) {
                log.info("ClassDetails  Show.   ");
                log.info("    ProgId--->>" + cds.getProgId());
                log.info("    Desp  --->>" + cds.getDescription());
                log.info("    ClsId --->>" + cds.getClsId());
            }
            log.info("-----------------------------------------------------------");
        } catch (JIException e) {
            log.error("获取配置文件中内容时出错",e);
        } catch (UnknownHostException e1) {
            log.error("Host无法识别或者格式错误",e1);
        }

        return classDetails;
    }

    /**
     * 同步指定item
     */
    public JIVariant syncTargetItem (Item item) {
        JIVariant ji = null;
        try {
            ji = item.read(false).getValue();
        } catch (JIException e) {
            log.error("获取" + item.getId() + "的实时值出错.",e);
        }
        return ji;
    }

    /**
     * 读取CSV中配置的测点名称，获取测点名称集合
     * @param file
     * @param columnNum
     * @return
     */
    public List<String> prepareProtocalData (File file, int columnNum, int rowIndex) {
        Map<Integer, String[]> map = ExcelImportHandler.getDataFromCsv(file,columnNum);

        List<String> codeList = new ArrayList<String>();
        for (Map.Entry<Integer, String[]> entry : map.entrySet()) {
            codeList.add(entry.getValue()[rowIndex]);
        }
        return codeList;
    }

    public static void main (String args[]) {
        UtgardOpcHelper ut = new UtgardOpcHelper();
        Collection<ClassDetails> classDetails = null;

        try {
            ConnectionInformation connectionInformation = new ConnectionInformation();
            connectionInformation.setHost("192.168.0.71");
            connectionInformation.setUser("Administrator");
            connectionInformation.setPassword("123456");
            connectionInformation.setDomain("");
            ServerList serverList = new ServerList(connectionInformation.getHost(),
                    connectionInformation.getUser(), connectionInformation.getPassword(),
                    connectionInformation.getDomain());

            /** According the progid get the clsid, then get the classdetail */
            /** Whatever the using DA agreement */
            classDetails = serverList
                    .listServersWithDetails(new Category[] {
                            Categories.OPCDAServer10, Categories.OPCDAServer20,
                            Categories.OPCDAServer30 }, new Category[] {});

            log.error("-----------------------------------------------------------");
            log.error("--------开始获取目标Ip：" + connectionInformation.getHost() + "下所有on service的opc服务.-----");
            for (ClassDetails cds : classDetails) {
                log.error("ClassDetails  Show.   ");
                log.error("    ProgId--->>" + cds.getProgId());
                log.error("    Desp  --->>" + cds.getDescription());
                log.error("    ClsId --->>" + cds.getClsId());
            }
            log.error("-----------------------------------------------------------");
        } catch (JIException e) {
            log.error("获取配置文件中内容时出错",e);
        } catch (UnknownHostException e1) {
            log.error("Host无法识别或者格式错误",e1);
        }
    }
}
