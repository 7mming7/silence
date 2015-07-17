package com.pnc.domain;

import java.io.Serializable;

/**
 * 实时测点.
 * User: shuiqing
 * Date: 2015/3/30
 * Time: 13:57
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class MesuringPoint implements Serializable {

    /**
     * 源code，DCS或者PLC系统上测点的编码
     */
    private String sourceCode;

    /**
     * 目标code, 接口对接方编码
     */
    private String targetCode;

    /**
     * 测点名称
     */
    private String pointName;

    /**
     * 测点数据类型
     */
    private DataType dataType;

    /**
     * 计算表达式
     */
    private String calculateExp;

    /**
     * 系统编号，表示opc服务的标示
     */
    private int sysId;

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getTargetCode() {
        return targetCode;
    }

    public void setTargetCode(String targetCode) {
        this.targetCode = targetCode;
    }

    public String getPointName() {
        return pointName;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    public String getCalculateExp() {
        return calculateExp;
    }

    public void setCalculateExp(String calculateExp) {
        this.calculateExp = calculateExp;
    }

    public int getSysId() {
        return sysId;
    }

    public void setSysId(int sysId) {
        this.sysId = sysId;
    }

    public MesuringPoint() {
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }
}
