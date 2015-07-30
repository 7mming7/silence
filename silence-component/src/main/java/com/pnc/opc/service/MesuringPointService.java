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

    /**
     * 读取server下所有的ITEM
     * @param cid
     */
    public Map<Item, ItemState> fetchReadSyncItems (final int cid) {
        Map<Item, ItemState> itemStateMap = new HashMap<Item, ItemState>();
        OpcServerInfomation opcServerInfomation = OpcRegisterFactory.fetchOpcInfo(cid);
        if (opcServerInfomation.getLeafs() == null) {
            opcServerInfomation.setLeafs(null);
            List<MesuringPoint> mesuringPointList = OpcRegisterFactory.registerMesuringPoint(cid);
            OpcRegisterFactory.registerConfigItems(cid, mesuringPointList);
        }
        Collection<Leaf> leafs = opcServerInfomation.getLeafs();
        Server server = opcServerInfomation.getServer();
        server.setDefaultUpdateRate(6000);
        Group group = null;
        final Item[] itemArr = new Item[leafs.size()];
        try {
            int item_flag = 0;
            group = server.addGroup();
            group.setActive(true);
            for(Leaf leaf:leafs){
                Item item = group.addItem(leaf.getItemId());
                item.setActive(true);
                itemArr[item_flag] = item;
                item_flag++;
            }

            itemStateMap = group.read(true, itemArr);
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
            log.error("key= " + entry.getKey().getId()
                    + " and value= " + entry.getValue().getValue().toString());
            String itemCode = entry.getKey().getId();
            String itemValue = entry.getValue().getValue().toString();
            MesuringPoint mesuringPoint = OpcRegisterFactory.fetchPointBySourceCode(itemCode);
            PointData pointData = new PointData();
            pointData.setIndex(mesuringPoint.getIndex());
            pointData.setItemCode(itemCode);
            pointData.setItemValue(itemValue);
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
        for(int i = Integer.parseInt(msgDataList.get(0).getIndex());
            i<= Integer.parseInt(msgDataList.get(msgDataList.size() - 1).getIndex());
            i = i + UdpSocketCfg.DATAPACKET_SIZE) {

            List<PointData> subPointDataList = msgDataList.subList(i,i+UdpSocketCfg.DATAPACKET_SIZE);
            List<String> packetDataList = new LinkedList<String>();
            for (PointData pointData:subPointDataList) {
                packetDataList.add(pointData.getItemValue());
            }

            SendMessage sendMessage = new SendMessage();
            sendMessage.setStartPos(i);
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
        BaseConfiguration baseConfiguration = new BaseConfiguration();
        baseConfiguration.init();
        ConnectionInformation connectionInformation = OpcRegisterFactory.fetchConnInfo(1);
        UtgardOpcHelper.findOpcServerList(
                connectionInformation.getHost(),
                connectionInformation.getUser(),
                connectionInformation.getPassword());
        Map<Item, ItemState> itemItemStateMap = mesuringPointService.syncOpcItemAllSystem();
        mesuringPointService.buildDataPacket(itemItemStateMap);
    }

}
