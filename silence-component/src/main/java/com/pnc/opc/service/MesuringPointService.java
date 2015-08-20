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

                /*String msg = "Macsv5.Device3.Group3.B1PT1311A,25.171757;Macsv5.Device3.Group3.B1PT1313B,9.016096;Macsv5.Device3.Group3.B3PT1314B,-56.9275;Macsv5.Device3.Group3.B3PT1315B,-8.901537;Macsv5.Device3.Group3.B3PT1316B,-132.8584;Macsv5.Device3.Group3.B3PT1317B,-140.64229;Macsv5.Device3.Group3.B3FT1521,2.088994;Macsv5.Device3.Group3.B3MF1031_CT,24.547464;Macsv5.Device3.Group3.B3FT1522,2.447657;Macsv5.Device3.Group3.B3PT1611,3.95716;Macsv5.Device3.Group3.NET1218,47.88076;Macsv5.Device3.Group3.B3CEMS_CO,0.28896952;Macsv5.Device3.Group3.B1PT1314B,-17.420174;Macsv5.Device3.Group3.B3ZQCHBY05,3.5803106;Macsv5.Device3.Group3.NET1219,0.33599272;Macsv5.Device3.Group3.NET1220,4.383179;Macsv5.Device3.Group3.B3TE1491,146.8935;Macsv5.Device3.Group3.B3TE1201A,949.93536;Macsv5.Device3.Group3.B3TE1211A,889.89734;Macsv5.Device3.Group3.B3TE1211C,1027.8158;Macsv5.Device3.Group3.B3TE1201B,1056.8616;Macsv5.Device3.Group3.B3TE1211B,898.9044;Macsv5.Device3.Group3.B3TE1211D,965.1199;Macsv5.Device3.Group3.B1PT1315B,-14.482856;Macsv5.Device3.Group3.B3TE1311A,526.3162;Macsv5.Device3.Group3.B3TE1312A,466.4019;Macsv5.Device3.Group3.B3TE1313A,411.5192;Macsv5.Device3.Group3.B3TE1314A,365.2268;Macsv5.Device3.Group3.B3TE1315A,318.48834;Macsv5.Device3.Group3.B3TE1316A,276.94107;Macsv5.Device3.Group3.B3TE1317A,241.7923;Macsv5.Device3.Group3.B3TE1311B,529.53613;Macsv5.Device3.Group3.B3TE1312B,481.9885;Macsv5.Device3.Group3.B3TE1313B,424.30063;Macsv5.Device3.Group3.B1PT1316B,-71.467636;Macsv5.Device3.Group3.B3TE1314B,369.70108;Macsv5.Device3.Group3.B3TE1315B,320.96375;Macsv5.Device3.Group3.B3TE1316B,275.33524;Macsv5.Device3.Group3.B3TE1317B,239.83382;Macsv5.Device3.Group3.B3TE1611,394.87848;Macsv5.Device3.Group3.B4PT1311A,-153.71376;Macsv5.Device3.Group3.B4PT1312A,-165.31616;Macsv5.Device3.Group3.B4PT1313A,-144.4608;Macsv5.Device3.Group3.B4PT1314A,-148.13286;Macsv5.Device3.Group3.B4PT1315A,-182.20615;Macsv5.Device3.Group3.B1PT1317B,-105.2472;Macsv5.Device3.Group3.B4PT1316A,-273.85168;Macsv5.Device3.Group3.B4PT1317A,-205.85197;Macsv5.Device3.Group3.B4PT1311B,-192.92737;Macsv5.Device3.Group3.B4PT1312B,-74.84555;Macsv5.Device3.Group3.B4PT1313B,-184.70288;Macsv5.Device3.Group3.B4PT1314B,-196.01154;Macsv5.Device3.Group3.B4PT1315B,-170.7502;Macsv5.Device3.Group3.B4PT1316B,-252.55573;Macsv5.Device3.Group3.B4PT1317B,-254.90558;Macsv5.Device3.Group3.B4FT1521,2.9684482;Macsv5.Device3.Group3.B1FT1521,2.6311636;Macsv5.Device3.Group3.B4MF1031_CT,26.726044;Macsv5.Device3.Group3.B4FT1522,4.0555615;Macsv5.Device3.Group3.B4PT1611,3.9861143;Macsv5.Device3.Group3.NET1318,94.46111;Macsv5.Device3.Group3.B4CEMS_CO,0.12111664;Macsv5.Device3.Group3.B4ZQCHBY05,5.323793;Macsv5.Device3.Group3.NET1319,0.6333365;Macsv5.Device3.Group3.NET1320,3.1623237;Macsv5.Device3.Group3.B4TE1491,145.52083;Macsv5.Device3.Group3.B4TE1201A,1041.4448;Macsv5.Device3.Group3.B1MF1031_CT,21.168974;Macsv5.Device3.Group3.B4TE1211A,915.74207;Macsv5.Device3.Group3.B4TE1211C,943.7941;Macsv5.Device3.Group3.B4TE1201B,1008.9472;Macsv5.Device3.Group3.B4TE1211B,980.2297;Macsv5.Device3.Group3.B4TE1211D,975.5809;Macsv5.Device3.Group3.B4TE1311A,533.444;Macsv5.Device3.Group3.B4TE1312A,476.3543;Macsv5.Device3.Group3.B4TE1313A,408.64145;Macsv5.Device3.Group3.B4TE1314A,355.06985;Macsv5.Device3.Group3.B4TE1315A,308.41937;Macsv5.Device3.Group3.B1FT1522,2.6050138;Macsv5.Device3.Group3.B4TE1316A,263.63818;Macsv5.Device3.Group3.B4TE1317A,229.97592;Macsv5.Device3.Group3.B4TE1311B,535.7301;Macsv5.Device3.Group3.B4TE1312B,476.7416;Macsv5.Device3.Group3.B4TE1313B,408.47913;Macsv5.Device3.Group3.B4TE1314B,357.98343;Macsv5.Device3.Group3.B4TE1315B,309.87366;Macsv5.Device3.Group3.B4TE1316B,263.63818;Macsv5.Device3.Group3.B4TE1317B,227.08295;Macsv5.Device3.Group3.B4TE1611,395.0531;Macsv5.Device3.Group3.B1PT1611,3.9307241;Macsv5.Device3.Group3.T1MS2311_CT,-0.009346008;Macsv5.Device3.Group3.T1MS2312_CT,31.963837;Macsv5.Device3.Group3.T1PT2281,0.53621733;Macsv5.Device3.Group3.T1PT2291,0.91749823;Macsv5.Device3.Group3.T1PT2361,-4.9721537;Macsv5.Device3.Group3.T1TE2281,161.67535;Macsv5.Device3.Group3.T1TE2291,180.50714;Macsv5.Device3.Group3.T1TE2361,90.45349;Macsv5.Device3.Group3.T2MS2311_CT,38.796436;Macsv5.Device3.Group3.T2MS2312_CT,-0.024032593;Macsv5.Device3.Group3.B1ZQCHBY02,34.427773;Macsv5.Device3.Group3.T2PT2281,0.7986076;Macsv5.Device3.Group3.T2PT2291,1.4135559;Macsv5.Device3.Group3.T2PT2361,18.33371;Macsv5.Device3.Group3.T2TE2281,246.57355;Macsv5.Device3.Group3.T2TE2291,317.7135;Macsv5.Device3.Group3.T2TE2361,102.274605;Macsv5.Device3.Group3.AM10MCS0206,45.312244;Macsv5.Device3.Group3.AM11MCS0206,47.05403;Macsv5.Device3.Group3.AM12MCS0206,50.491238;Macsv5.Device3.Group3.AM15LL01,123.759125;Macsv5.Device3.Group3.B1PT1312A,-2.733171;Macsv5.Device3.Group3.B1CEMS_CO,0.8436173;Macsv5.Device3.Group3.AM13MCS0206,50.722824;Macsv5.Device3.Group3.B1MNAI004,3.7769458;Macsv5.Device3.Group3.B1MNAI020,-12.369751;Macsv5.Device3.Group3.B1MNAI021,-50.347168;Macsv5.Device3.Group3.B1MNAI022,-35.15625;Macsv5.Device3.Group3.B1MNAI033,990.99744;Macsv5.Device3.Group3.B1MNAI034,262.0949;Macsv5.Device3.Group3.B1MNAI035,427.8646;Macsv5.Device3.Group3.B1MNAI036,253.73264;Macsv5.Device3.Group3.B1MNAI037,176.88078;Macsv5.Device3.Group3.B1ZQCHAI07R,2.5281053;Macsv5.Device3.Group3.B1MNAI038,162.26852;Macsv5.Device3.Group3.B1MNAI047,153.50478;Macsv5.Device3.Group3.B1MNAI066,333.36227;Macsv5.Device3.Group3.B1MNAI067,440.7118;Macsv5.Device3.Group3.B1MNAI068,339.0625;Macsv5.Device3.Group3.B1MNAI069,174.9132;Macsv5.Device3.Group3.B1MNAI070,163.71529;Macsv5.Device3.Group3.B1MNAI077,142.68663;Macsv5.Device3.Group3.B1MNAI093,12.796585;Macsv5.Device3.Group3.B2MNAI004,7.058377;Macsv5.Device3.Group3.B1ZQCHBY03,0.7330041;Macsv5.Device3.Group3.B2MNAI020,-143.55469;Macsv5.Device3.Group3.B2MNAI021,-111.65369;Macsv5.Device3.Group3.B2MNAI022,-187.60852;Macsv5.Device3.Group3.B2MNAI033,942.1943;Macsv5.Device3.Group3.B2MNAI034,202.60417;Macsv5.Device3.Group3.B2MNAI035,252.63309;Macsv5.Device3.Group3.B2MNAI036,168.31596;Macsv5.Device3.Group3.B2MNAI037,169.76273;Macsv5.Device3.Group3.B2MNAI038,169.90741;Macsv5.Device3.Group3.B2MNAI047,134.06033;Macsv5.Device3.Group3.B1ZQCHBY04,3.2877882;Macsv5.Device3.Group3.B2MNAI066,176.5625;Macsv5.Device3.Group3.B2MNAI067,180.70023;Macsv5.Device3.Group3.B2MNAI068,176.73611;Macsv5.Device3.Group3.B2MNAI069,168.98148;Macsv5.Device3.Group3.B2MNAI070,177.22801;Macsv5.Device3.Group3.B2MNAI077,143.8585;Macsv5.Device3.Group3.B2MNAI093,12.850115;Macsv5.Device3.Group3.B3MNAI004,6.3539853;Macsv5.Device3.Group3.B3MNAI020,12.478394;Macsv5.Device3.Group3.B3MNAI021,-69.98694;Macsv5.Device3.Group3.B1TE1201A,1089.6168;Macsv5.Device3.Group3.B3MNAI022,-39.27954;Macsv5.Device3.Group3.B3MNAI033,990.26074;Macsv5.Device3.Group3.B3MNAI034,324.91318;Macsv5.Device3.Group3.B3MNAI035,421.7882;Macsv5.Device3.Group3.B3MNAI036,191.17477;Macsv5.Device3.Group3.B3MNAI037,185.99538;Macsv5.Device3.Group3.B3MNAI038,181.25;Macsv5.Device3.Group3.B3MNAI047,142.73003;Macsv5.Device3.Group3.B3MNAI066,312.35532;Macsv5.Device3.Group3.B3MNAI067,311.1111;Macsv5.Device3.Group3.B1TE1211A,1040.7993;Macsv5.Device3.Group3.B3MNAI068,191.26158;Macsv5.Device3.Group3.B3MNAI069,188.59953;Macsv5.Device3.Group3.B3MNAI070,187.32639;Macsv5.Device3.Group3.B3MNAI077,147.16797;Macsv5.Device3.Group3.B3MNAI093,12.974538;Macsv5.Device3.Group3.B4MNAI004,6.5086083;Macsv5.Device3.Group3.B4MNAI020,-194.44446;Macsv5.Device3.Group3.B4MNAI021,-114.04077;Macsv5.Device3.Group3.B4MNAI022,-175.99829;Macsv5.Device3.Group3.B4MNAI033,950.94055;Macsv5.Device3.Group3.B1TE1211C,902.35065;Macsv5.Device3.Group3.B4MNAI034,338.91782;Macsv5.Device3.Group3.B4MNAI035,277.4016;Macsv5.Device3.Group3.B4MNAI036,180.38194;Macsv5.Device3.Group3.B4MNAI037,180.06366;Macsv5.Device3.Group3.B4MNAI038,175.05788;Macsv5.Device3.Group3.B4MNAI047,148.75217;Macsv5.Device3.Group3.B4MNAI066,317.07175;Macsv5.Device3.Group3.B4MNAI067,121.93288;Macsv5.Device3.Group3.B4MNAI068,178.64584;Macsv5.Device3.Group3.B4MNAI069,178.0382;Macsv5.Device3.Group3.B1TE1201B,1016.7921;Macsv5.Device3.Group3.B4MNAI070,170.08102;Macsv5.Device3.Group3.B4MNAI077,144.42274;Macsv5.Device3.Group3.B4MNAI093,12.310475;Macsv5.Device3.Group3.B1YQWD07,151.0;Macsv5.Device3.Group3.B1YQWD08,148.0;Macsv5.Device3.Group3.B1YQWD09,149.0;Macsv5.Device3.Group3.B1YQWD10,154.0;Macsv5.Device3.Group3.B1YQWD11,153.0;Macsv5.Device3.Group3.B1YQWD12,147.0;Macsv5.Device3.Group3.B1YQWD14,819.0;Macsv5.Device3.Group3.B1TE1211B,1069.8656;Macsv5.Device3.Group3.B2YQWD07,155.0;Macsv5.Device3.Group3.B2YQWD08,154.0;Macsv5.Device3.Group3.B2YQWD09,155.0;Macsv5.Device3.Group3.B2YQWD10,154.0;Macsv5.Device3.Group3.B2YQWD11,154.0;Macsv5.Device3.Group3.B2YQWD12,156.0;Macsv5.Device3.Group3.B2YQWD14,797.0;Macsv5.Device3.Group3.B3YQWD14,748.0;Macsv5.Device3.Group3.B4YQWD14,1026.0;Macsv5.Device3.Group3.GFAI006,11.0;Macsv5.Device3.Group3.B1TE1211D,947.47687;Macsv5.Device3.Group3.GFAI007,11.0;Macsv5.Device3.Group3.GFAI008,11.0;Macsv5.Device3.Group3.GFAI009,12.0;Macsv5.Device3.Group3.GFAI018,0.0;Macsv5.Device3.Group3.AM14BY001,66.5505;Macsv5.Device3.Group3.AM14BY002,0.0;Macsv5.Device3.Group3.AM14BY003,0.0;Macsv5.Device3.Group3.AM14BY004,0.0;Macsv5.Device3.Group3.AM14BY005,40.429123;Macsv5.Device3.Group3.AM15BY001,-67.99179;Macsv5.Device3.Group3.B1PT1313A,4.3163896;Macsv5.Device3.Group3.B1TE1311A,0;Macsv5.Device3.Group3.AM15BY002,0.0;Macsv5.Device3.Group3.AM15BY003,0.0;Macsv5.Device3.Group3.AM15BY004,0.0;Macsv5.Device3.Group3.AM15BY005,0.0;Macsv5.Device3.Group3.DEHAI004,268.0;Macsv5.Device3.Group3.DEHAI005,301.0;Macsv5.Device3.Group3.DEHAI304,329.0;Macsv5.Device3.Group3.DEHAI305,312.0;Macsv5.Device3.Group3.AMDMG004,5386.2;Macsv5.Device3.Group3.AMDMG005,4758.6;Macsv5.Device3.Group3.B1TE1312A,454.46286;Macsv5.Device3.Group3.AMDMG006,4915.0;Macsv5.Device3.Group3.AMDMG007,4634.8;Macsv5.Device3.Group3.AM10LJ01,395883.44;Macsv5.Device3.Group3.AM11LJ01,409194.12;Macsv5.Device3.Group3.AM12LJ01,418959.0;Macsv5.Device3.Group3.AM13LJ01,382447.2;Macsv5.Device3.Group3.AM10LJ02,436044.56;Macsv5.Device3.Group3.AM11LJ02,446474.66;Macsv5.Device3.Group3.AM12LJ02,441355.53;Macsv5.Device3.Group3.AM13LJ02,406810.44;Macsv5.Device3.Group3.B1TE1313A,393.78015;Macsv5.Device3.Group3.B1TE1511,126.76609;Macsv5.Device3.Group3.B2TE1511,126.375946;Macsv5.Device3.Group3.B3TE1511,126.603546;Macsv5.Device3.Group3.B4TE1511,126.636055;Macsv5.Device3.Group3.B1MNAI007,202.0978;Macsv5.Device3.Group3.B2MNAI007,211.82004;Macsv5.Device3.Group3.B3MNAI007,216.78241;Macsv5.Device3.Group3.B4MNAI007,214.03355;Macsv5.Device3.Group3.AM14ZQQ,702928.9;Macsv5.Device3.Group3.AM15ZQQ,881423.9;Macsv5.Device3.Group3.B1TE1314A,344.06796;Macsv5.Device3.Group3.T1TE2011,393.44183;Macsv5.Device3.Group3.T2TE2011,391.9929;Macsv5.Device3.Group3.T1PT2011,3.896315;Macsv5.Device3.Group3.T2PT2011,3.8195982;Macsv5.Device3.Group3.AMGYYQAI07,-92.45;Macsv5.Device3.Group3.SCRAI68Q,-91.7;Macsv5.Device3.Group3.DMCS003,41611.0;Macsv5.Device3.Group3.DMCS2003,25538.0;Macsv5.Device3.Group3.DMCS30013,66771.0;Macsv5.Device3.Group3.DMCS4003,48356.0;Macsv5.Device3.Group3.B1TE1315A,303.22787;Macsv5.Device3.Group3.DMCS004,35790.0;Macsv5.Device3.Group3.DMCS2004,59793.0;Macsv5.Device3.Group3.DMCS30014,92338.0;Macsv5.Device3.Group3.DMCS4004,29571.0;Macsv5.Device3.Group3.DMCS005,18407.0;Macsv5.Device3.Group3.DMCS2005,29031.0;Macsv5.Device3.Group3.DMCS30015,9609.0;Macsv5.Device3.Group3.DMCS006,106.0;Macsv5.Device3.Group3.DMCS2006,78.0;Macsv5.Device3.Group3.B1TE1316A,298.69345;Macsv5.Device3.Group3.DMCS30016,1.0;Macsv5.Device3.Group3.DMCS4006,24.0;Macsv5.Device3.Group3.DMCS007,1050.0;Macsv5.Device3.Group3.DMCS2007,633.0;Macsv5.Device3.Group3.DMCS30017,1116.0;Macsv5.Device3.Group3.DMCS4007,848.0;Macsv5.Device3.Group3.DMCS008,670.0;Macsv5.Device3.Group3.DMCS2008,260.0;Macsv5.Device3.Group3.DMCS30018,1769.0;Macsv5.Device3.Group3.DMCS4008,597.0;Macsv5.Device3.Group3.B1TE1317A,287.70032;Macsv5.Device3.Group3.DMCS009,4624.0;Macsv5.Device3.Group3.DMCS2009,3321.0;Macsv5.Device3.Group3.DMCS30019,2150.0;Macsv5.Device3.Group3.DMCS4009,2735.0;Macsv5.Device3.Group3.DMCS001,13023.0;Macsv5.Device3.Group3.DMCS2001,9879.0;Macsv5.Device3.Group3.DMCS3001,12361.0;Macsv5.Device3.Group3.DMCS4001,13477.0;Macsv5.Device3.Group3.DMCS002,22400.0;Macsv5.Device3.Group3.DMCS2002,34844.0;Macsv5.Device3.Group3.B1TE1311B,516.66565;";*/
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
