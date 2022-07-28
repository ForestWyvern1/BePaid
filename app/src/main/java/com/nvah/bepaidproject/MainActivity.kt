package com.nvah.bepaidproject

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.begateway.mobilepayments.BuildConfig
import com.begateway.mobilepayments.models.network.request.*
import com.begateway.mobilepayments.models.network.response.BeGatewayResponse
import com.begateway.mobilepayments.sdk.OnResultListener
import com.begateway.mobilepayments.sdk.PaymentSdk
import com.begateway.mobilepayments.sdk.PaymentSdkBuilder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nvah.bepaidproject.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnResultListener {
    private lateinit var binding: ActivityMainBinding

    private var isWithCheckout: Boolean = false
    private var sdk: PaymentSdk? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        listeners()
    }

    private fun initPaymentSdk() = PaymentSdkBuilder().apply {
        setDebugMode(BuildConfig.DEBUG)
        with(binding) {
            setPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxiq93sRjfWUiS/OE2ZPfMSAeRZFGpVVetqkwQveG0reIiGnCl4RPJGMH1ng3y3ekhTxO1Ze+ln3sCK0LJ/MPrR1lzKN9QbY4F3l/gmj/XLUseOPFtayxvQaC+lrYcnZbTFhqxB6I1MSF/3oeTqbjJvUE9KEDmGsZ57y0+ivbRo9QJs63zoKaUDpQSKexibSMu07nm78DOORvd0AJa/b5ZF+6zWFolVBmzuIgGDpCWG+Gt4+LSw9yiH0/43gieFr2rDKbb7e7JQpnyGEDT+IRP9uKCmlRoV1kHcVyHoNbC0Q9kV8jPW2K5rKuj80auV3I2dgjJEsvxMuHQOr4aoMAgQIDAQAB")
            setCardNumberFieldVisibility(true)
            setCardCVCFieldVisibility(true)
            setCardDateFieldVisibility(true)
            setCardHolderFieldVisibility(true)
        }
        setEndpoint("https://checkout.bepaid.by/ctp/api/")
    }.build().apply {
        addCallBackListener(this@MainActivity)
    }.also {
        sdk = it
    }

    private fun listeners() {
        binding.bGetToken.setOnClickListener {
            pay()
        }
    }

    private fun isProgressVisible(isVisible: Boolean) {
        binding.flProgressBar.isVisible = isVisible
    }

    private fun payWithCheckout() {
        startActivity(
            PaymentSdk.getCardFormIntent(this@MainActivity)
        )
    }

    private fun pay() {
        isProgressVisible(true)
        CoroutineScope(Dispatchers.IO).launch {
            initPaymentSdk().getPaymentToken(
                TokenCheckoutData(
                    Checkout(
                        test = BuildConfig.DEBUG,// true only if you work in test mode
                        transactionType = TransactionType.PAYMENT,
                        order = Order(
                            amount = 100,
                            currency = "USD",
                            description = "Payment description",
                            trackingId = "merchant_id",
                            additionalData = AdditionalData(
                                contract = arrayOf(
                                    Contract.RECURRING,
                                    Contract.CARD_ON_FILE
                                )
                            )
                        ),
                        settings = Settings(
                            autoReturn = 0,
                            returnUrl = "https://DEFAULT_RETURN_URL.com",
                            saveCardPolicy = SaveCardPolicy(
                                true
                            )
                        ),
                    ),
                ).apply {
                    addCustomField("customField", "custom string")
                }
            )
        }
    }

    override fun onDestroy() {
        sdk?.removeResultListener(this)
        super.onDestroy()
    }

    override fun onTokenReady(token: String) {
        if (isWithCheckout) {
            payWithCheckout()
            isWithCheckout = false
        } else {
            startActivity(
                PaymentSdk.getCardFormIntent(this@MainActivity)
            )
        }
        isProgressVisible(false)
    }

    private fun getPreferences() = getSharedPreferences("BE_PAID_PREFS", Context.MODE_PRIVATE)

    override fun onPaymentFinished(beGatewayResponse: BeGatewayResponse, cardToken: String?) {
        if (!isFinishing) {
            cardToken?.let {
                getPreferences().edit { putString("be_paid_card_token", cardToken) }
            }
            isWithCheckout = false
            getMessageDialog(
                this,
                "Result",
                beGatewayResponse.message + "; card token=" + cardToken,
                positiveOnClick = { dialog, _ ->
                    dialog.dismiss()
                },
                isCancellableOutside = false
            ).show()
            isProgressVisible(false)
        }
    }

    private fun getMessageDialog(
        context: Context,
        title: String? = null,
        message: String? = null,
        positiveButtonText: String = "ok",
        negativeButtonText: String? = null,
        positiveOnClick: DialogInterface.OnClickListener? = null,
        onCancelClick: DialogInterface.OnClickListener? = null,
        isCancellableOutside: Boolean = false
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(
            context
        )
        title?.let {
            builder.setTitle(it)
        }
        message?.let {
            builder.setMessage(it)
        }
        positiveOnClick?.let {
            builder.setPositiveButton(positiveButtonText, it)
        }
        onCancelClick?.let {
            builder.setNegativeButton(negativeButtonText, it)
        }
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(isCancellableOutside)
        return dialog
    }
}