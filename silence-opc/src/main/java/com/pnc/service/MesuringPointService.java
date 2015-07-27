package com.pnc.service;

import com.pnc.component.BaseConfiguration;
import com.pnc.component.OpcRegisterFactory;
import com.pnc.domain.MesuringPoint;
import com.pnc.domain.OpcDataType;
import com.pnc.domain.OpcServerInfomation;
import com.pnc.excel.handler.ExcelImportHandler;
import org.jinterop.dcom.common.JIException;
import org.openscada.opc.lib.common.NotConnectedException;
import org.openscada.opc.lib.da.*;
import org.openscada.opc.lib.da.browser.Leaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    public void fetchReadSyncItems (final int cid) {
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
                System.out.println("ItemName:[" + item.getId()
                        + "],value:" + item.read(true).getValue());
                itemArr[item_flag] = item;
                item_flag++;
            }
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
    }
}
