package com.pnc.socket;

import java.net.DatagramPacket;

/**
 * 处理udp请求的接口
 * User: shuiqing
 * Date: 2015/7/28
 * Time: 11:40
 * Email: shuiqing301@gmail.com
 * _
 * |_)._ _
 * | o| (_
 */
public interface IUdpRequestHandler{

    /**
     * 解析请求数据包
     * @param requestPack
     */
    void parse(DatagramPacket requestPack);
}
