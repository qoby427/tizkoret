package com.emhillstudio.tizcoret;

import android.os.Bundle;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupportActivity extends MessageActivity implements PurchasesUpdatedListener {

    private BillingClient billingClient;

    private static final List<String> DONATION_IDS = Arrays.asList(
            "donation_small",
            "donation_medium",
            "donation_large"
    );

    private final Map<String, ProductDetails> donationDetails = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);

        // Initialize BillingClient
        billingClient = BillingClient.newBuilder(this)
                .enablePendingPurchases()
                .setListener(this)
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryDonationProducts();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Optional: retry logic
            }
        });

        findViewById(R.id.btnDonateSmall).setOnClickListener(v ->
                launchDonation("donation_small")
        );

        findViewById(R.id.btnDonateMedium).setOnClickListener(v ->
                launchDonation("donation_medium")
        );

        findViewById(R.id.btnDonateLarge).setOnClickListener(v ->
                launchDonation("donation_large")
        );
    }

    // Load all donation products
    private void queryDonationProducts() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();

        for (String id : DONATION_IDS) {
            products.add(
                    QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
            );
        }

        QueryProductDetailsParams params =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(products)
                        .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                donationDetails.clear();
                for (ProductDetails pd : productDetailsList) {
                    donationDetails.put(pd.getProductId(), pd);
                }
            }
        });
    }

    // Launch purchase for selected tier
    private void launchDonation(String productId) {
        ProductDetails pd = donationDetails.get(productId);
        if (pd == null) {
            showMessage("Donation options not loaded yet. Try again.", false);
            return;
        }

        BillingFlowParams.ProductDetailsParams params =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build();

        BillingFlowParams flowParams =
                BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(Collections.singletonList(params))
                        .build();

        billingClient.launchBillingFlow(this, flowParams);
    }

    // Purchase callback
    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        }
    }

    // Acknowledge purchase
    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {

            showMessage("Thank you for your support!", true);

            AcknowledgePurchaseParams params =
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build();

            billingClient.acknowledgePurchase(params, billingResult -> {
                // Optional logging
            });
        }
    }
}
