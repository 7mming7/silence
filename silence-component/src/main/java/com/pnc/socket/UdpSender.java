package com.pnc.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * UDP数据发送服务.
 * User: shuiqing
 * Date: 2015/7/30
 * Time: 15:12
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class UdpSender {

    private static final Logger log = LoggerFactory.getLogger(UdpSender.class);

    public static DatagramSocket datagramSocket;

    static {
        datagramSocket = initUdpSenderService();
    }

    /**
     * 初始化UDP数据发送器
     * @return
     */
    public static DatagramSocket initUdpSenderService () {
        UdpSocketCfg.loadConfigProperties();
        return UdpSocketCfg.initUdpServer();
    }

    /**
     * 拼装 待发送的数据包
     * @param sendMsg
     * @return
     */
    public static DatagramPacket assemblyDatagramPacket(String sendMsg) {
        byte[] sendByteMsg = sendMsg.getBytes();
        DatagramPacket datagramPacket = null;
        try {
            datagramPacket = new DatagramPacket(
                    sendByteMsg,
                    sendByteMsg.length,
                    InetAddress.getByAddress(UdpSocketCfg.RECEIVE_IP.getBytes()),
                    UdpSocketCfg.RECEIVE_PORT);
        } catch (UnknownHostException e) {
            log.error("发送的目标地址错误.",e);
        }
        return datagramPacket;
    }
}
