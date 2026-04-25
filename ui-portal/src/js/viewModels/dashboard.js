define([
    'knockout',
    'hooks/useAuth',
    'hooks/usePayments',
    'ojs/ojarraydataprovider',
    'ojs/ojknockout',
    'ojs/ojtable',
    'ojs/ojinputtext',
    'ojs/ojinputnumber',
    'ojs/ojbutton',
    'ojs/ojformlayout'
], function(ko, useAuth, usePayments, ArrayDataProvider) {
    function DashboardViewModel() {
        const { isAuthenticated, login, getAuthHeader } = useAuth();
        const { payments, createPayment, fetchPayments, isLoading } = usePayments();

        this.username = ko.observable('admin');
        this.password = ko.observable('admin123');
        this.isAuthenticated = isAuthenticated;

        this.amount = ko.observable(100.00);
        this.currency = ko.observable('USD');
        
        this.dataprovider = new ArrayDataProvider(payments, {
            keyAttributes: 'orderId'
        });

        this.handleLogin = async () => {
            const success = await login(this.username(), this.password());
            if (success) {
                fetchPayments();
            }
        };

        this.handleCreatePayment = async () => {
            const idempotencyKey = 'key-' + Date.now();
            await createPayment(this.amount(), this.currency(), idempotencyKey);
        };

        // Initial fetch if already logged in
        if (isAuthenticated()) {
            fetchPayments();
        }
    }

    return DashboardViewModel;
});
