package com.pnc.opc.service;

import com.pnc.opc.component.BaseConfiguration;
import com.pnc.opc.component.OpcRegisterFactory;
import com.pnc.opc.component.PointDataComparator;
import com.pnc.opc.component.UtgardOpcHelper;
import com.pnc.opc.domain.MesuringPoint;
import com.pnc.opc.domain.OpcServerInfomation;
import com.pnc.opc.domain.PointData;
import com.pnc.opc.domain.SendMessage;
import com.pnc.socket.UdpSender;
import com.pnc.socket.UdpSocketCfg;
import org.jinterop.dcom.common.JIException;
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
            OpcRegisterFactory.registerConfigItems(cid, mesuringPointList);
        }
        Collection<Leaf> leafs = opcServerInfomation.getLeafs();
        Server server = opcServerInfomation.getServer();
        try {
            if (!flag) {
                itemArr = new Item[leafs.size()];
                int item_flag = 0;
                group = server.addGroup();
                group.setActive(true);
                for(Leaf leaf:leafs){
                    Item item = group.addItem(leaf.getItemId());
                    item.setActive(true);
                    itemArr[item_flag] = item;
                    item_flag++;
                }
            }
            long start1 = System.currentTimeMillis();
            log.error("1、拼装item[]用时：" + (start1 - start) + "ms");
            itemStateMap = group.read(true, itemArr);
            log.error("2、group read 用时：" + (System.currentTimeMillis() - start1) + "ms");
        } catch (UnknownHostException e) {
            log.error("Host unknow error.",e);
        } catch (NotConnectedException e) {
            log.error("Connnect to opc error.",e);
        } catch (JIException e) {
            log.error("Opc server connect error.",e);
        } catch (DuplicateGroupException e) {
            log.error("Group duplicate error.",e);
        } catch (AddFailedException e) {
            log.error("Group add error.",e);
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
        List<PointData> msgDataList = new LinkedList<PointData>();
        for (Map.Entry<Item, ItemState> entry : syncItems.entrySet()) {
            String itemCode = entry.getKey().getId();
            String itemValue = entry.getValue().getValue().toString();
            MesuringPoint mesuringPoint = OpcRegisterFactory.fetchPointBySourceCode(itemCode);
            PointData pointData = new PointData();
            pointData.setIndex(mesuringPoint.getIndex());
            pointData.setItemCode(itemCode);
            pointData.setItemValue(itemValue.substring(2, itemValue.length() - 2));
            msgDataList.add(pointData);
        }
        Collections.sort(msgDataList,new PointDataComparator());
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
        for(int i = 0;
            i< msgDataList.size();
            i = i + UdpSocketCfg.DATAPACKET_SIZE) {

            List<PointData> subPointDataList = new LinkedList<PointData>();
            if (msgDataList.size() <= i+UdpSocketCfg.DATAPACKET_SIZE) {
                subPointDataList = msgDataList.subList(i,msgDataList.size());
            } else {
                subPointDataList = msgDataList.subList(i,i+UdpSocketCfg.DATAPACKET_SIZE);
            }
            List<String> packetDataList = new LinkedList<String>();
            for (PointData pointData:subPointDataList) {
                packetDataList.add(pointData.getItemValue());
            }

            SendMessage sendMessage = new SendMessage();
            sendMessage.setStartPos(Integer.parseInt(msgDataList.get(0).getIndex()));
            sendMessage.setPointAmount(packetDataList.size());
            sendMessage.setData(packetDataList);

            DatagramPacket datagramPacket = UdpSender.assemblyDatagramPacket(sendMessage.genSendMessage());
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
        while (true) {
            try {
                Thread.sleep(100l);//获取数据的间隔时间
                long start = System.currentTimeMillis();

                Map<Item, ItemState> itemItemStateMap = mesuringPointService.syncOpcItemAllSystem();
                long time1 = System.currentTimeMillis();
                log.error("3、同步数据总耗时-- " + (time1 - start) + "ms");
                mesuringPointService.buildDataPacket(itemItemStateMap);
                long time2 = System.currentTimeMillis();
                log.error("4、拼装和发送数据包总耗时-- " + (time2 - time1) + "ms");
                log.error("-------------------------------------------------");
                log.error("");
            } catch (InterruptedException e) {
                log.error("获取数据sleep出错.", e);
            }
        }
    }
}