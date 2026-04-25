define([], function() {
    /**
     * @class PaymentOrder
     * @param {Object} data 
     */
    function PaymentOrder(data) {
        this.orderId = data.orderId;
        this.amount = data.amount;
        this.currency = data.currency;
        this.status = data.status;
        this.createdAt = data.createdAt;
        this.idempotencyKey = data.idempotencyKey;
    }

    return PaymentOrder;
});
