package com.mycelium.wallet.external.changelly;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.BuildConfig;
import com.mycelium.wallet.external.changelly.ChangellyAPIService.ChangellyAnswerDouble;
import com.mycelium.wallet.external.changelly.ChangellyAPIService.ChangellyTransaction;
import com.mycelium.wallet.external.changelly.ChangellyAPIService.ChangellyTransactionOffer;

import java.io.IOException;

import retrofit2.Call;

public class ChangellyService extends IntentService {
    private static final String LOG_TAG="ChangellyService";
    private static final String PACKAGE_NAME = "com.mycelium.wallet.external.changelly";
    public static final String ACTION_GET_EXCHANGE_AMOUNT = PACKAGE_NAME + ".GETEXCHANGEAMOUNT";
    public static final String ACTION_CREATE_TRANSACTION = PACKAGE_NAME + ".CREATETRANSACTION";

    public static final String INFO_EXCH_AMOUNT = PACKAGE_NAME + ".INFOEXCHAMOUNT";
    public static final String INFO_TRANSACTION = PACKAGE_NAME + ".INFOTRANSACTION";
    public static final String INFO_ERROR       = PACKAGE_NAME + ".INFOERROR";

    public static final String BCH = "BCH";
    public static final String BTC = "BTC";

    public static final String FROM = "FROM";
    public static final String TO = "TO";
    public static final String AMOUNT = "AMOUNT";
    public static final String DESTADDRESS = "DESTADDRESS";
    public static final String OFFER = "OFFER";
    public static final String FROM_AMOUNT = "FROM_AMOUNT";

    private ChangellyAPIService changellyAPIService = ChangellyAPIService.retrofit.create(ChangellyAPIService.class);

    public ChangellyService() {
        super("ChangellyService");
    }

    public static void start(Context context, String action, String fromCurrency, String toCurrency, Double amount, Address destinationAddress) {
        Intent intent = new Intent(context, ChangellyService.class).setAction(action);
        if (fromCurrency != null) {
            intent.putExtra(ChangellyService.FROM, fromCurrency);
        }
        if (toCurrency != null) {
            intent.putExtra(ChangellyService.TO, toCurrency);
        }
        if (amount != null) {
            intent.putExtra(ChangellyService.AMOUNT, amount);
        }
        if (destinationAddress != null) {
            intent.putExtra(ChangellyService.DESTADDRESS, destinationAddress.toString());
        }
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "some channel name 1";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("ContentTitle")
                    .setContentText("ContentText").build();

            startForeground(1, notification);
        }
    }

    private double getExchangeAmount(String from, String to, double amount) {
        Call<ChangellyAnswerDouble> call3 = changellyAPIService.getExchangeAmount(from, to, amount);
        try {
            ChangellyAnswerDouble result = call3.execute().body();
            if(result != null) {
                Log.d("MyceliumChangelly", "You will receive the following " + to + " amount: " + result.result);
                return result.result;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // return txid?
    private ChangellyTransactionOffer createTransaction(String from, String to, double amount, String destAddress) {
        if (BuildConfig.FLAVOR.equals("btctestnet")) {
            ChangellyTransactionOffer result = new ChangellyTransactionOffer();
            result.amountFrom = amount;
            result.amountTo = getExchangeAmount(from, to, amount);
            result.currencyFrom = from;
            result.currencyTo = to;
            result.payinAddress = "bchtest:qrntcnsl8p2y936cu9eq9nwhnj9uvsg9wvcv2k60yp";
            result.payoutAddress = destAddress;
            result.id = "test_order_id";
            return result;
        }
        Call<ChangellyTransaction> call4 = changellyAPIService.createTransaction(from, to, amount, destAddress);
        try {
            ChangellyTransaction result = call4.execute().body();
            if(result != null) {
                //Log.d("MyceliumChangelly", "createTransaction answer: " + result.result);
                return result.result;
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "createTransaction", e);
        }
        return null;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.i(LOG_TAG, "onHandleIntent: ${intent?.action}");
        if (intent != null && intent.getAction() != null) {
            String from, to, destAddress;
            double amount;
            switch(intent.getAction()) {
                case ACTION_GET_EXCHANGE_AMOUNT:
                    from = intent.getStringExtra(FROM);
                    to = intent.getStringExtra(TO);
                    amount = intent.getDoubleExtra(AMOUNT, 0);
                    double offer = getExchangeAmount(from, to, amount);
                    if(offer == -1) {
                        // service unavailable
                        Intent errorIntent = new Intent(ChangellyService.INFO_ERROR, null,
                                this, ChangellyService.class);
                        LocalBroadcastManager.getInstance(this).sendBroadcast(errorIntent);
                        return;
                    }
                    // service available
                    Intent exchangeAmountIntent = new Intent(ChangellyService.INFO_EXCH_AMOUNT, null,
                            this, ChangellyService.class)
                            .putExtra(FROM, from)
                            .putExtra(TO, to)
                            .putExtra(FROM_AMOUNT, amount)
                            .putExtra(AMOUNT, offer);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(exchangeAmountIntent);
                    break;
                case ACTION_CREATE_TRANSACTION:
                    from = intent.getStringExtra(FROM);
                    to = intent.getStringExtra(TO);
                    amount = intent.getDoubleExtra(AMOUNT, 0);
                    destAddress = intent.getStringExtra(DESTADDRESS);
                    ChangellyTransactionOffer res = createTransaction(from, to, amount, destAddress);
                    Intent transactionIntent;
                    if(res == null) {
                        // service unavailable
                        transactionIntent = new Intent(ChangellyService.INFO_ERROR, null,
                                this, ChangellyService.class);
                    } else {
                        // service available
                        // example answer
                        //{"jsonrpc":"2.0","id":"test","result":{"id":"39526c0eb6ba","apiExtraFee":"0","changellyFee":"0.5","payinExtraId":null,"status":"new","currencyFrom":"eth","currencyTo":"BTC","amountTo":0,"payinAddress":"0xdd0a917944efc6a371829053ad318a6a20ee1090","payoutAddress":"1J3cP281yiy39x3gcPaErDR6CSbLZZKzGz","createdAt":"2017-11-22T18:47:19.000Z"}}
                        transactionIntent = new Intent(ChangellyService.INFO_TRANSACTION, null,
                                this, ChangellyService.class);
                        res.amountFrom = amount;
                        transactionIntent.putExtra(OFFER, res);
                    }
                    LocalBroadcastManager.getInstance(this).sendBroadcast(transactionIntent);
                    break;
            }
        }
    }
}
