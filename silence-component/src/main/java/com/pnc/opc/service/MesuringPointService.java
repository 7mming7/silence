package com.pnc.opc.service;

import com.pnc.opc.component.BaseConfiguration;
import com.pnc.opc.component.OpcRegisterFactory;
import com.pnc.opc.component.PointDataComparator;
import com.pnc.opc.component.UtgardOpcHelper;
import com.pnc.opc.domain.MesuringPoint;
import com.pnc.opc.domain.OpcServerInfomation;
import com.pnc.opc.domain.PointData;
import com.pnc.opc.domain.SendMessage;
import com.pnc.socket.SocketConsts;
import com.pnc.socket.UdpSender;
import com.pnc.socket.UdpSocketCfg;
import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.core.JIUnsignedInteger;
import org.jinterop.dcom.core.JIUnsignedShort;
import org.jinterop.dcom.core.JIVariant;
import org.jsoup.Connection;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.common.NotConnectedException;
import org.openscada.opc.lib.da.*;
import org.openscada.opc.lib.da.browser.Leaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: shuiqing
 * Date: 2015/7/27
 * Time: 11:43
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class MesuringPointService {

    private static final Logger log = LoggerFactory.getLogger(MesuringPointService.class);

    public static Group group;

    public static Item[] itemArr = null;

    /**
     * 读取server下所有的ITEM
     * @param cid
     */
    public Map<Item, ItemState> fetchReadSyncItems (final int cid) {
        long start = System.currentTimeMillis();
        Map<Item, ItemState> itemStateMap = new HashMap<Item, ItemState>();
        OpcServerInfomation opcServerInfomation = OpcRegisterFactory.fetchOpcInfo(cid);

        boolean flag = true;
        if (opcServerInfomation.getLeafs() == null) {
            flag = false;
            opcServerInfomation.setLeafs(null);
            List<MesuringPoint> mesuringPointList = OpcRegisterFactory.registerMesuringPoint(cid);
            log.error("mesuringPointList:----" + mesuringPointList.size());
            OpcRegisterFactory.registerConfigItems(cid, mesuringPointList);
        }
        Collection<Leaf> leafs = opcServerInfomation.getLeafs();
        log.error("leafs:----" + leafs.size());
        Server server = opcServerInfomation.getServer();
        try {
            if (!flag) {
                itemArr = new Item[leafs.size()];
                int item_flag = 0;
                group = server.addGroup();
                group.setActive(true);
                for(Leaf leaf:leafs){
                    Item item = null;
                    try {
                        item = group.addItem(leaf.getItemId());
                    } catch (AddFailedException e) {
                        log.error("Group add error.Error item is ：" + leaf.getItemId(),e);
                    }
                    item.setActive(true);
                    itemArr[item_flag] = item;
                    item_flag++;
                }
                log.error("itemArr:---" + itemArr.length);
                int i = 1;
                for (Item item:itemArr) {
                    log.error(item.getId());
                    i++;
                }
            }
            long start1 = System.currentTimeMillis();
            log.error("     1、拼装item[]用时：" + (start1 - start) + "ms");
            itemStateMap = group.read(true, itemArr);
            log.error("     2、group read 用时：" + (System.currentTimeMillis() - start1) + "ms");
        } catch (UnknownHostException e) {
            log.error("Host unknow error.",e);
        } catch (NotConnectedException e) {
            log.error("Connnect to opc error.",e);
        } catch (JIException e) {
            log.error("Opc server connect error.",e);
        } catch (DuplicateGroupException e) {
            log.error("Group duplicate error.",e);
        }
        return itemStateMap;
    }

    /**
     * 各OPC系统数据同步
     */
    public Map<Item, ItemState> syncOpcItemAllSystem () {
        Map<Item, ItemState> itemItemStateMap = new HashMap<Item, ItemState>();
        for (int i=1;i <= BaseConfiguration.CONFIG_CLIENT_MAX;i++) {
            itemItemStateMap.putAll(this.fetchReadSyncItems(i));
        }
        return itemItemStateMap;
    }

    /**
     * 构建消息数据包
     * @param syncItems
     */
    public void buildDataPacket (Map<Item, ItemState> syncItems) {
        log.error("syncItems:----" + syncItems.size());
        List<PointData> msgDataList = new LinkedList<PointData>();
        for (Map.Entry<Item, ItemState> entry : syncItems.entrySet()) {
            String itemCode = entry.getKey().getId();
            String itemValue = entry.getValue().getValue().toString();
            try {
                JIVariant jiVariant = entry.getValue().getValue();
                if (jiVariant.getType() == 18) {
                    JIUnsignedShort jiUnsignedShort = (JIUnsignedShort)jiVariant.getObject();
                    log.error("&&&&&type-18  -->  " + jiUnsignedShort.getValue().toString());
                    itemValue = jiUnsignedShort.getValue().toString();
                } else if (jiVariant.getType() == 19) {
                    JIUnsignedInteger jiUnsignedInteger = (JIUnsignedInteger)jiVariant.getObject();
                    log.error("&&&&&type-19  -->  " + jiUnsignedInteger.getValue().toString());
                    itemValue = jiUnsignedInteger.getValue().toString();
                } else {
                    itemValue = jiVariant.getObject().toString();
                }
            } catch (JIException e) {
                e.printStackTrace();
            }

            if (itemValue.contains("org.jinterop.dcom.core.VariantBody$EMPTY")) {
                itemValue = "0";
            }
            MesuringPoint mesuringPoint = OpcRegisterFactory.fetchPointBySourceCode(itemCode);
            PointData pointData = new PointData();
            pointData.setIndex(mesuringPoint.getIndex());
            pointData.setItemCode(itemCode);
            pointData.setItemValue(itemValue);
            msgDataList.add(pointData);
        }
        Collections.sort(msgDataList, new PointDataComparator());
        int i = 1;
        for (PointData pointData:msgDataList) {
            if (i != Integer.parseInt(pointData.getIndex())) {
                log.error("missing index : " + i);
                i = Integer.parseInt(pointData.getIndex()) + 1;
            } else {
                i = i + 1;
            }
        }
        pointDataUnpackSend(msgDataList);
    }

    /**
     * 将获取的点数据进行拆包
     * @param msgDataList
     * @return
     */
    public List<DatagramPacket> pointDataUnpackSend (List<PointData> msgDataList) {
        List<DatagramPacket> datagramPacketList = new ArrayList<DatagramPacket>();
        if (msgDataList.isEmpty() || msgDataList.size() == 0) {
            return null;
        }
        int counter = 0;
        for(int i = 0;
            i< msgDataList.size();
            i = i + UdpSocketCfg.DATAPACKET_SIZE) {

            List<PointData> subPointDataList = new LinkedList<PointData>();
            if (msgDataList.size() <= i+UdpSocketCfg.DATAPACKET_SIZE) {
                subPointDataList = msgDataList.subList(i,msgDataList.size());
            } else {
                subPointDataList = msgDataList.subList(i,i+UdpSocketCfg.DATAPACKET_SIZE);
            }
            counter++;
            List<PointData> packetDataList = new LinkedList<PointData>();
            for (PointData pointData:subPointDataList) {
                log.error("add point index:" + pointData.getIndex());
                packetDataList.add(pointData);
            }


            System.out.println(counter + "   counter--------------------------------------------------");
            SendMessage sendMessage = new SendMessage();
            sendMessage.setStartPos(counter);
            sendMessage.setPointAmount(packetDataList.size());
            sendMessage.setData(packetDataList);

            String sendMsg = "";
            if (UdpSocketCfg.SENDER_FOR_SYSTEM == SocketConsts.SENDER_FOR_SYSTEM_INSIDE) {
                sendMsg = sendMessage.genSendMessageForInside();
            } else if (UdpSocketCfg.SENDER_FOR_SYSTEM == SocketConsts.SENDER_FOR_SYSTEM_LUCENT) {
                sendMsg = sendMessage.genSendMessageForLucent();
            }

            DatagramPacket datagramPacket = UdpSender.assemblyDatagramPacket(sendMsg);
            try {
                UdpSender.datagramSocket.send(datagramPacket);
            } catch (IOException e) {
                log.error("IO 通讯异常.", e);
            }
            datagramPacketList.add(datagramPacket);
        }
        return datagramPacketList;
    }

    public static void main(String[] args) {
        MesuringPointService mesuringPointService = new MesuringPointService();

        BaseConfiguration.init();
        UdpSender.init();

        for (int i=1;i<=BaseConfiguration.CONFIG_CLIENT_MAX;i++) {
            UtgardOpcHelper.fetchClassDetails(i);
        }
        while (true) {
            try {
                Thread.sleep(1000l);//获取数据的间隔时间

                /*String msg = "Macsv5.Device3.Group3.DEHAI001-2999.0;Macsv5.Device3.Group3.DEHAI010-0.0;Macsv5.Device3.Group3.B1PT1314A--23.88227;Macsv5.Device3.Group3.B1PT1315A--92.61674;Macsv5.Device3.Group3.B1PT1316A--136.67691;Macsv5.Device3.Group3.B1PT1317A--125.51511;Macsv5.Device3.Group3.B1PT1311B--25.938393;Macsv5.Device3.Group3.B1PT1312B--34.309746;Macsv5.Device3.Group3.B1PT1313B-6.519377;Macsv5.Device3.Group3.B1PT1314B--19.916893;Macsv5.Device3.Group3.B1PT1315B--31.813026;Macsv5.Device3.Group3.B1PT1316B--83.95123;Macsv5.Device3.Group3.DEHAI011-7.0;Macsv5.Device3.Group3.B1PT1317B--121.990326;Macsv5.Device3.Group3.B1FT1521-3.8737764;Macsv5.Device3.Group3.B1MF1031_CT-26.801785;Macsv5.Device3.Group3.B1FT1522-3.6882517;Macsv5.Device3.Group3.B1PT1611-3.969959;Macsv5.Device3.Group3.B1ZQCHBY02-41.8319;Macsv5.Device3.Group3.B1CEMS_CO-0.8477925;Macsv5.Device3.Group3.B1ZQCHAI07R-5.6012263;Macsv5.Device3.Group3.B1ZQCHBY03-0.08592741;Macsv5.Device3.Group3.B1ZQCHBY04-3.0620809;Macsv5.Device3.Group3.DEHAI012-5.0;Macsv5.Device3.Group3.B1TE1491-168.34576;Macsv5.Device3.Group3.B1TE1201A-884.0375;Macsv5.Device3.Group3.B1TE1211A-933.2427;Macsv5.Device3.Group3.B1TE1211C-931.0288;Macsv5.Device3.Group3.B1TE1201B-969.34436;Macsv5.Device3.Group3.B1TE1211B-951.98285;Macsv5.Device3.Group3.B1TE1211D-919.3399;Macsv5.Device3.Group3.B1TE1311A-0;Macsv5.Device3.Group3.B1TE1312A-486.69165;Macsv5.Device3.Group3.B1TE1313A-421.706;Macsv5.Device3.Group3.DEHAI0131-1.61;Macsv5.Device3.Group3.B1TE1314A-369.17502;Macsv5.Device3.Group3.B1TE1315A-324.81342;Macsv5.Device3.Group3.B1TE1316A-321.55304;Macsv5.Device3.Group3.B1TE1317A-316.2703;Macsv5.Device3.Group3.B1TE1311B-549.56085;Macsv5.Device3.Group3.B1TE1312B-481.08237;Macsv5.Device3.Group3.B1TE1313B-416.1259;Macsv5.Device3.Group3.B1TE1314B-369.01175;Macsv5.Device3.Group3.B1TE1315B-320.20276;Macsv5.Device3.Group3.B1TE1316B-279.24585;Macsv5.Device3.Group3.DEHAI0141-1.63;Macsv5.Device3.Group3.B1TE1317B-246.95125;Macsv5.Device3.Group3.B1TE1611-402.71744;Macsv5.Device3.Group3.B2PT1311A--52.37466;Macsv5.Device3.Group3.B2PT1312A--85.86049;Macsv5.Device3.Group3.B2PT1313A--73.81749;Macsv5.Device3.Group3.B2PT1314A-24.437428;Macsv5.Device3.Group3.B2PT1315A--87.91661;Macsv5.Device3.Group3.B2PT1316A--88.797806;Macsv5.Device3.Group3.B2PT1317A--108.77198;Macsv5.Device3.Group3.B2PT1311B--7.8734756;Macsv5.Device3.Group3.DEHAI015--9225.0;Macsv5.Device3.Group3.B2PT1312B-48.817574;Macsv5.Device3.Group3.B2PT1313B--79.985855;Macsv5.Device3.Group3.B2PT1314B-6.813109;Macsv5.Device3.Group3.B2PT1315B--28.87571;Macsv5.Device3.Group3.B2PT1316B--90.41333;Macsv5.Device3.Group3.B2PT1317B--153.1263;Macsv5.Device3.Group3.B2FT1521-2.251489;Macsv5.Device3.Group3.B2MF1031_CT-20.19629;Macsv5.Device3.Group3.B2FT1522-2.2635088;Macsv5.Device3.Group3.B2PT1611-3.9579992;Macsv5.Device3.Group3.DEHAI0161-0.0;Macsv5.Device3.Group3.NET1118-70.5938;Macsv5.Device3.Group3.B2CEMS_CO-0.08201599;Macsv5.Device3.Group3.B2ZQCHBY05-6.60304;Macsv5.Device3.Group3.NET1119-0.21947168;Macsv5.Device3.Group3.NET1120-1.3807144;Macsv5.Device3.Group3.B2TE1491-143.9847;Macsv5.Device3.Group3.B2TE1201A-1003.36646;Macsv5.Device3.Group3.B2TE1211A-1000.5386;Macsv5.Device3.Group3.B2TE1211C-1044.521;Macsv5.Device3.Group3.B2TE1201B-1069.2615;Macsv5.Device3.Group3.DEHAI0171-0.0;Macsv5.Device3.Group3.B2TE1211B-976.59863;Macsv5.Device3.Group3.B2TE1211D-971.2521;Macsv5.Device3.Group3.B2TE1311A-523.33734;Macsv5.Device3.Group3.B2TE1312A-454.76004;Macsv5.Device3.Group3.B2TE1313A-393.98206;Macsv5.Device3.Group3.B2TE1314A-344.56546;Macsv5.Device3.Group3.B2TE1315A-300.55716;Macsv5.Device3.Group3.B2TE1316A-254.0909;Macsv5.Device3.Group3.B2TE1317A-222.01602;Macsv5.Device3.Group3.B2TE1311B-529.68066;Macsv5.Device3.Group3.DEHAI0181-6.25;Macsv5.Device3.Group3.B2TE1312B-463.96463;Macsv5.Device3.Group3.B2TE1313B-395.54593;Macsv5.Device3.Group3.B2TE1314B-342.5358;Macsv5.Device3.Group3.B2TE1315B-301.18515;Macsv5.Device3.Group3.B2TE1316B-256.79028;Macsv5.Device3.Group3.B2TE1317B-225.21532;Macsv5.Device3.Group3.B2TE1611-393.98206;Macsv5.Device3.Group3.B3PT1311A--23.88227;Macsv5.Device3.Group3.B3PT1312A-6.959975;Macsv5.Device3.Group3.B3PT1313A--68.677185;Macsv5.Device3.Group3.DEHAI0191-7.08;Macsv5.Device3.Group3.B3PT1314A--80.13272;Macsv5.Device3.Group3.B3PT1315A--123.60585;Macsv5.Device3.Group3.B3PT1316A--92.7636;Macsv5.Device3.Group3.B3PT1317A--86.88855;Macsv5.Device3.Group3.B3PT1311B-50.873695;Macsv5.Device3.Group3.B3PT1312B--112.44363;Macsv5.Device3.Group3.B3PT1313B--27.553917;Macsv5.Device3.Group3.B3PT1314B--72.78943;Macsv5.Device3.Group3.B3PT1315B--47.674953;Macsv5.Device3.Group3.B3PT1316B--157.3854;Macsv5.Device3.Group3.DEHAI002-2999.0;Macsv5.Device3.Group3.DEHAI0201-1.08;Macsv5.Device3.Group3.B3PT1317B--168.84094;Macsv5.Device3.Group3.B3FT1521-1.8376824;Macsv5.Device3.Group3.B3MF1031_CT-23.853827;Macsv5.Device3.Group3.B3FT1522-3.556494;Macsv5.Device3.Group3.B3PT1611-3.980869;Macsv5.Device3.Group3.NET1218-46.708725;Macsv5.Device3.Group3.B3CEMS_CO-0.2952491;Macsv5.Device3.Group3.B3ZQCHBY05-4.1586494;Macsv5.Device3.Group3.NET1219-2.2406204;Macsv5.Device3.Group3.NET1220-3.9808168;Macsv5.Device3.Group3.DEHAI0211-1.08;Macsv5.Device3.Group3.B3TE1491-149.4428;Macsv5.Device3.Group3.B3TE1201A-948.3779;Macsv5.Device3.Group3.B3TE1211A-910.5394;Macsv5.Device3.Group3.B3TE1211C-1067.6694;Macsv5.Device3.Group3.B3TE1201B-1071.2286;Macsv5.Device3.Group3.B3TE1211B-904.48175;Macsv5.Device3.Group3.B3TE1211D-1132.4722;Macsv5.Device3.Group3.B3TE1311A-530.28326;Macsv5.Device3.Group3.B3TE1312A-468.24808;Macsv5.Device3.Group3.B3TE1313A-411.81796;Macsv5.Device3.Group3.DEHAI022-78.0;Macsv5.Device3.Group3.B3TE1314A-362.88193;Macsv5.Device3.Group3.B3TE1315A-312.744;Macsv5.Device3.Group3.B3TE1316A-274.57254;Macsv5.Device3.Group3.B3TE1317A-243.08133;Macsv5.Device3.Group3.B3TE1311B-537.46375;Macsv5.Device3.Group3.B3TE1312B-483.51016;Macsv5.Device3.Group3.B3TE1313B-424.04807;Macsv5.Device3.Group3.B3TE1314B-364.6782;Macsv5.Device3.Group3.B3TE1315B-313.7686;Macsv5.Device3.Group3.B3TE1316B-272.73254;Macsv5.Device3.Group3.DEHAI023-63.0;Macsv5.Device3.Group3.B3TE1317B-241.29297;Macsv5.Device3.Group3.B3TE1611-397.84998;Macsv5.Device3.Group3.B4PT1311A-80.247284;Macsv5.Device3.Group3.B4PT1312A-71.28805;Macsv5.Device3.Group3.B4PT1313A-83.037735;Macsv5.Device3.Group3.B4PT1314A-101.54325;Macsv5.Device3.Group3.B4PT1315A-70.55372;Macsv5.Device3.Group3.B4PT1316A--19.035698;Macsv5.Device3.Group3.B4PT1317A-45.145927;Macsv5.Device3.Group3.B4PT1311B-43.38312;Macsv5.Device3.Group3.DEHAI024-69.0;Macsv5.Device3.Group3.B4PT1312B-165.137;Macsv5.Device3.Group3.B4PT1313B-56.74833;Macsv5.Device3.Group3.B4PT1314B-42.79566;Macsv5.Device3.Group3.B4PT1315B-83.33147;Macsv5.Device3.Group3.B4PT1316B-2.2602677;Macsv5.Device3.Group3.B4PT1317B--5.0830245;Macsv5.Device3.Group3.B4FT1521-2.2424972;Macsv5.Device3.Group3.B4MF1031_CT-31.794777;Macsv5.Device3.Group3.B4FT1522-2.5504892;Macsv5.Device3.Group3.B4PT1611-3.9823377;Macsv5.Device3.Group3.DEHAI025-62.0;Macsv5.Device3.Group3.NET1318-100.97515;Macsv5.Device3.Group3.B4CEMS_CO-32.872707;Macsv5.Device3.Group3.B4ZQCHBY05-5.1672325;Macsv5.Device3.Group3.NET1319-0.71594137;Macsv5.Device3.Group3.NET1320-5.96951;Macsv5.Device3.Group3.B4TE1491-141.50075;Macsv5.Device3.Group3.B4TE1201A-1054.7677;Macsv5.Device3.Group3.B4TE1211A-886.99927;Macsv5.Device3.Group3.B4TE1211C-898.36926;Macsv5.Device3.Group3.B4TE1201B-965.3077;Macsv5.Device3.Group3.DEHAI026-55.0;Macsv5.Device3.Group3.B4TE1211B-1004.49365;Macsv5.Device3.Group3.B4TE1211D-942.36914;Macsv5.Device3.Group3.B4TE1311A-534.7002;Macsv5.Device3.Group3.B4TE1312A-470.70667;Macsv5.Device3.Group3.B4TE1313A-401.23972;Macsv5.Device3.Group3.B4TE1314A-351.01102;Macsv5.Device3.Group3.B4TE1315A-305.8088;Macsv5.Device3.Group3.B4TE1316A-264.04016;Macsv5.Device3.Group3.B4TE1317A-231.91649;Macsv5.Device3.Group3.B4TE1311B-521.6916;Macsv5.Device3.Group3.DEHAI027-90.0;Macsv5.Device3.Group3.B4TE1312B-457.27695;Macsv5.Device3.Group3.B4TE1313B-395.41205;Macsv5.Device3.Group3.B4TE1314B-351.1747;Macsv5.Device3.Group3.B4TE1315B-305.27997;Macsv5.Device3.Group3.B4TE1316B-263.6387;Macsv5.Device3.Group3.B4TE1317B-230.38489;Macsv5.Device3.Group3.B4TE1611-386.02856;Macsv5.Device3.Group3.T1MS2311_CT-40.754936;Macsv5.Device3.Group3.T1MS2312_CT--2.590029;Macsv5.Device3.Group3.T1PT2281-0.5903487;Macsv5.Device3.Group3.DEHAI028-80.0;Macsv5.Device3.Group3.T1PT2291-1.0219843;Macsv5.Device3.Group3.T1PT2361--4.9721537;Macsv5.Device3.Group3.T1TE2281-164.3041;Macsv5.Device3.Group3.T1TE2291-184.04689;Macsv5.Device3.Group3.T1TE2361-92.2501;Macsv5.Device3.Group3.T2MS2311_CT--3.1134207;Macsv5.Device3.Group3.T2MS2312_CT-40.89971;Macsv5.Device3.Group3.T2PT2281-0.7586175;Macsv5.Device3.Group3.T2PT2291-1.1797838;Macsv5.Device3.Group3.T2PT2361-6.013584;Macsv5.Device3.Group3.DEHAI029-68.0;Macsv5.Device3.Group3.T2TE2281-243.80836;Macsv5.Device3.Group3.T2TE2291-314.9626;Macsv5.Device3.Group3.T2TE2361-99.6931;Macsv5.Device3.Group3.AM10MCS0206-46.32982;Macsv5.Device3.Group3.AM11MCS0206-40.83519;Macsv5.Device3.Group3.AM12MCS0206-47.482803;Macsv5.Device3.Group3.AM15LL01-98.93737;Macsv5.Device3.Group3.AM13MCS0206-40.85545;Macsv5.Device3.Group3.B1MNAI004-4.787869;Macsv5.Device3.Group3.B1MNAI020--37.217896;Macsv5.Device3.Group3.DEHAI003-2999.0;Macsv5.Device3.Group3.DEHAI030-71.0;Macsv5.Device3.Group3.B1MNAI021--63.151;Macsv5.Device3.Group3.B1MNAI022--45.898438;Macsv5.Device3.Group3.B1MNAI033-961.091;Macsv5.Device3.Group3.B1MNAI034-244.90741;Macsv5.Device3.Group3.B1MNAI035-266.2905;Macsv5.Device3.Group3.B1MNAI036-180.96065;Macsv5.Device3.Group3.B1MNAI037-165.68288;Macsv5.Device3.Group3.B1MNAI038-169.96529;Macsv5.Device3.Group3.B1MNAI047-187.4132;Macsv5.Device3.Group3.B1MNAI066-130.6713;Macsv5.Device3.Group3.DEHAI031-76.0;Macsv5.Device3.Group3.B1MNAI067-320.94907;Macsv5.Device3.Group3.B1MNAI068-190.65393;Macsv5.Device3.Group3.B1MNAI069-171.6146;Macsv5.Device3.Group3.B1MNAI070-167.70834;Macsv5.Device3.Group3.B1MNAI077-196.97266;Macsv5.Device3.Group3.B1MNAI093-12.76331;Macsv5.Device3.Group3.B2MNAI004-6.310583;Macsv5.Device3.Group3.B2MNAI020--36.458374;Macsv5.Device3.Group3.B2MNAI021--5.0998535;Macsv5.Device3.Group3.B2MNAI022--88.97571;Macsv5.Device3.Group3.DEHAI032-67.0;Macsv5.Device3.Group3.B2MNAI033-963.00183;Macsv5.Device3.Group3.B2MNAI034-343.63425;Macsv5.Device3.Group3.B2MNAI035-276.35995;Macsv5.Device3.Group3.B2MNAI036-197.88773;Macsv5.Device3.Group3.B2MNAI037-169.99422;Macsv5.Device3.Group3.B2MNAI038-160.12732;Macsv5.Device3.Group3.B2MNAI047-193.8802;Macsv5.Device3.Group3.B2MNAI066-283.07294;Macsv5.Device3.Group3.B2MNAI067-211.57408;Macsv5.Device3.Group3.B2MNAI068-182.46529;Macsv5.Device3.Group3.DEHAI033-64.0;Macsv5.Device3.Group3.B2MNAI069-173.29283;Macsv5.Device3.Group3.B2MNAI070-166.49306;Macsv5.Device3.Group3.B2MNAI077-196.69054;Macsv5.Device3.Group3.B2MNAI093-12.86169;Macsv5.Device3.Group3.B3MNAI004-5.9552226;Macsv5.Device3.Group3.B3MNAI020--5.9678955;Macsv5.Device3.Group3.B3MNAI021--94.72656;Macsv5.Device3.Group3.B3MNAI022--64.12756;Macsv5.Device3.Group3.B3MNAI033-960.8867;Macsv5.Device3.Group3.B3MNAI034-107.89931;Macsv5.Device3.Group3.DEHAI034-53.0;Macsv5.Device3.Group3.B3MNAI035-307.60995;Macsv5.Device3.Group3.B3MNAI036-257.58102;Macsv5.Device3.Group3.B3MNAI037-195.74654;Macsv5.Device3.Group3.B3MNAI038-167.4479;Macsv5.Device3.Group3.B3MNAI047-190.29947;Macsv5.Device3.Group3.B3MNAI066-306.33682;Macsv5.Device3.Group3.B3MNAI067-327.89352;Macsv5.Device3.Group3.B3MNAI068-202.92247;Macsv5.Device3.Group3.B3MNAI069-196.58565;Macsv5.Device3.Group3.B3MNAI070-169.03935;Macsv5.Device3.Group3.DEHAI035-62.0;Macsv5.Device3.Group3.B3MNAI077-207.85591;Macsv5.Device3.Group3.B3MNAI093-13.0136;Macsv5.Device3.Group3.B4MNAI004-3.8890698;Macsv5.Device3.Group3.B4MNAI020-30.70752;Macsv5.Device3.Group3.B4MNAI021-107.638794;Macsv5.Device3.Group3.B4MNAI022-42.86023;Macsv5.Device3.Group3.B4MNAI033-1000.5264;Macsv5.Device3.Group3.B4MNAI034-153.29861;Macsv5.Device3.Group3.B4MNAI035-260.79282;Macsv5.Device3.Group3.B4MNAI036-211.11111;Macsv5.Device3.Group3.DEHAI036-62.0;Macsv5.Device3.Group3.B4MNAI037-174.68172;Macsv5.Device3.Group3.B4MNAI038-152.8646;Macsv5.Device3.Group3.B4MNAI047-187.78212;Macsv5.Device3.Group3.B4MNAI066-236.05325;Macsv5.Device3.Group3.B4MNAI067-169.64699;Macsv5.Device3.Group3.B4MNAI068-172.04861;Macsv5.Device3.Group3.B4MNAI069-159.75116;Macsv5.Device3.Group3.B4MNAI070-149.62384;Macsv5.Device3.Group3.B4MNAI077-176.48654;Macsv5.Device3.Group3.B4MNAI093-12.585359;";

                String[] pointArray = msg.split(";");
                for (String point:pointArray) {
                    String[] strAy = point.split("-");
                    System.out.println("code:" + strAy[0] + "      value:" + strAy[1]);
                }*/


                /*DatagramPacket datagramPacket = UdpSender.assemblyDatagramPacket(msg);
                try {
                    UdpSender.datagramSocket.send(datagramPacket);
                } catch (IOException e) {
                    log.error("IO 通讯异常.", e);
                }*/

                log.error("数据转发时间分布：");
                long start = System.currentTimeMillis();

                Map<Item, ItemState> itemItemStateMap = mesuringPointService.syncOpcItemAllSystem();
                long time1 = System.currentTimeMillis();
                log.error("     3、同步数据总耗时-- " + (time1 - start) + "ms");
                mesuringPointService.buildDataPacket(itemItemStateMap);
                long time2 = System.currentTimeMillis();
                log.error("     4、拼装和发送数据包总耗时-- " + (time2 - time1) + "ms");
                log.error("-------------------------------------------------");
                log.error("");
            } catch (InterruptedException e) {
                log.error("获取数据sleep出错.", e);
            }
        }
    }
}
