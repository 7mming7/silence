package com.pnc.socket;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * UDP接收器.
 * User: shuiqing
 * Date: 2015/7/28
 * Time: 14:06
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class UDPReceptor implements Runnable{

    private String hostname = "localhost";

    private int port = 7777;

    private int recePacketSize = 512;

    private IUdpRequestHandler requestHandler = null;

    /**
     * 过程
     */
    public void run() {
        DatagramSocket udpRece = null;
        DatagramPacket dataPack = null;
        byte[] buff = null;

        try{
            udpRece = new DatagramSocket(new InetSocketAddress(this.hostname, this.port));
            udpRece.setReceiveBufferSize(this.recePacketSize);

            for(;;){
                buff = new byte[this.recePacketSize];
                dataPack = new DatagramPacket(buff, this.recePacketSize);
                udpRece.receive(dataPack);
                if(null!=requestHandler) requestHandler.parse(dataPack);
            }
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    /**
     * 注入请求处理
     * @param requestHandler 请求处理
     */
    public void setRequestHandler(IUdpRequestHandler requestHandler){
        this.requestHandler = requestHandler;
    }

    /**
     * 设置接收包大小
     * @param udpPacketSize
     */
    public void setRecePacketSize(int udpPacketSize){
        this.recePacketSize = udpPacketSize;
    }

    public void setHostname(String hostname){
        this.hostname = hostname;
    }

    public void setPort(int port){
        this.port = port;
    }
}