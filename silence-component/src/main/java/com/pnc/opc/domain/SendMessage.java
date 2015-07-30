package com.pnc.opc.domain;

import java.util.List;

/**
 * 发送的数据体.
 * User: shuiqing
 * Date: 2015/7/30
 * Time: 16:17
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class SendMessage {

    public String head = "FFFFFFFF";

    public long startPos;

    public long pointAmount;

    public List<String> data;

    public String getHead() {
        return head;
    }

    public void setHead(String head) {
        this.head = head;
    }

    public long getStartPos() {
        return startPos;
    }

    public void setStartPos(long startPos) {
        this.startPos = startPos;
    }

    public long getPointAmount() {
        return pointAmount;
    }

    public void setPointAmount(long pointAmount) {
        this.pointAmount = pointAmount;
    }

    public List<String> getData() {
        return data;
    }

    public void setData(List<String> data) {
        this.data = data;
    }

    public String genSendMessage () {
        StringBuilder sb = new StringBuilder();
        sb.append(this.head)
                .append(";")
                .append(this.startPos)
                .append(";")
                .append(this.pointAmount)
                .append(";");
        for (String str:data) {
            sb.append(str).append(";");
        }
        return sb.toString();
    }
}
