package com.appmetr.s2s.events;

public class Payment extends Action {
    private static final String ACTION = "trackPayment";

    private String orderId;
    private String transactionId;
    private String processor;
    private String psUserSpentCurrencyCode;
    private String psUserSpentCurrencyAmount;
    private String appCurrencyCode;
    private String appCurrencyAmount;

    public Payment(String orderId,
                   String transactionId,
                   String processor,
                   String psUserSpentCurrencyCode,
                   String psUserSpentCurrencyAmount) {
        this(orderId, transactionId, processor, psUserSpentCurrencyCode, psUserSpentCurrencyAmount, null, null);
    }

    public Payment(String orderId,
                   String transactionId,
                   String processor,
                   String psUserSpentCurrencyCode,
                   String psUserSpentCurrencyAmount,
                   String appCurrencyCode,
                   String appCurrencyAmount) {
        super(ACTION);

        this.orderId = orderId;
        this.transactionId = transactionId;
        this.processor = processor;
        this.psUserSpentCurrencyCode = psUserSpentCurrencyCode;
        this.psUserSpentCurrencyAmount = psUserSpentCurrencyAmount;
        this.appCurrencyCode = appCurrencyCode;
        this.appCurrencyAmount = appCurrencyAmount;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getProcessor() {
        return processor;
    }

    public String getPsUserSpentCurrencyCode() {
        return psUserSpentCurrencyCode;
    }

    public String getPsUserSpentCurrencyAmount() {
        return psUserSpentCurrencyAmount;
    }

    public String getAppCurrencyCode() {
        return appCurrencyCode;
    }

    public String getAppCurrencyAmount() {
        return appCurrencyAmount;
    }

    @Override public int calcApproximateSize() {
        return super.calcApproximateSize()
                + getStringLength(orderId)
                + getStringLength(transactionId)
                + getStringLength(processor)
                + getStringLength(psUserSpentCurrencyCode)
                + getStringLength(psUserSpentCurrencyAmount)
                + getStringLength(appCurrencyCode)
                + getStringLength(appCurrencyAmount);
    }
}
