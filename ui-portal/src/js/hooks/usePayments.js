define(['knockout', 'hooks/useAuth'], function(ko, useAuth) {
    /**
     * Hook for payment operations
     */
    function usePayments() {
        const { getAuthHeader } = useAuth();
        const payments = ko.observableArray([]);
        const isLoading = ko.observable(false);

        const fetchPayments = async () => {
            isLoading(true);
            try {
                // For demo, we just fetch from order-service
                const response = await fetch('http://localhost:8081/api/orders', {
                    headers: { ...getAuthHeader() }
                });
                if (response.ok) {
                    const data = await response.json();
                    payments(data);
                }
            } catch (error) {
                console.error('Fetch Error:', error);
            } finally {
                isLoading(false);
            }
        };

        const createPayment = async (amount, currency, idempotencyKey) => {
            try {
                const response = await fetch('http://localhost:8081/api/orders', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        ...getAuthHeader() 
                    },
                    body: JSON.stringify({ amount, currency, idempotencyKey })
                });
                
                if (response.ok) {
                    const newOrder = await response.json();
                    payments.unshift(newOrder);
                    return newOrder;
                }
            } catch (error) {
                console.error('Create Error:', error);
            }
            return null;
        };

        return {
            payments,
            isLoading,
            fetchPayments,
            createPayment
        };
    }

    return usePayments;
});
